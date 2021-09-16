package ch.epfl.scala.index
package server
package routes
package api
package impl

import scala.concurrent.Future

import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.data.LocalPomRepository
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.maven.ReleaseModel
import ch.epfl.scala.index.data.project.ProjectConvert
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.index.search.ESRepo
import ch.epfl.scala.services.DatabaseApi
import org.slf4j.LoggerFactory
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.StashBuffer

object IndexingActor {
  def apply(
    paths: DataPaths,
    dataRepository: ESRepo,
    db: DatabaseApi,
  ): Behavior[UpdateIndex] = {
    def ready: Behavior[UpdateIndex] = Behaviors.receive { (ac: ActorContext[UpdateIndex], updateIndexData: UpdateIndex) =>

      import ac.executionContext

      val f = updateIndex(
        paths,
        db,
        updateIndexData.repo,
        updateIndexData.pom,
        updateIndexData.data,
        updateIndexData.localRepo
      )(ac).map(_ => ())

      Behaviors.withStash(Int.MaxValue){ (buffer: StashBuffer[UpdateIndex]) =>
        Behaviors.receiveMessage { (newMessage: UpdateIndex) =>
          buffer.stash(newMessage)
          if (f.isCompleted) buffer.unstashAll(ready)
          else Behaviors.same
        }
      }
    }
    ready
  }

  private val log = LoggerFactory.getLogger(getClass)
  /**
   * Main task to update the scaladex index.
   * - download GitHub info if allowd
   * - download GitHub contributors if allowed
   * - download GitHub readme if allowed
   * - search for project and
   *   1. update project
   *      1. Search for release
   *      2. update or create new release
   *   2. create new project
   *
   * @param repo the Github repo reference model
   * @param pom the Maven Model
   * @param data the main publish data
   * @return
   */
  private def updateIndex(
      paths: DataPaths,
      db: DatabaseApi,
      repo: GithubRepo,
      pom: ReleaseModel,
      data: PublishData,
      localRepository: LocalPomRepository
  )(implicit ac: ActorContext[UpdateIndex]): Future[Unit] = {
    ac.log.debug("updating " + pom.artifactId)

    val githubDownload = new GithubDownload(paths, Some(data.credentials))(ac.system.classicSystem)
    githubDownload.run(
      repo,
      data.downloadInfo,
      data.downloadReadme,
      data.downloadContributors
    )

    val projectReference = Project.Reference(repo.organization, repo.repository)
    import ac.executionContext

    for {
      project <- db.findProject(projectReference)
      converter = new ProjectConvert(paths, githubDownload)
      converted = converter.convertOne(
        pom,
        localRepository,
        data.hash,
        data.created,
        repo,
        project
      )
      _ = converted
        .map { case (p, r, d) =>
          for {
            _ <- db.insertOrUpdateProject(p)
            _ <- db.insertReleases(
              Seq(r)
            ) // todo: filter already existing releases , to only update them
            _ <- db.insertDependencies(
              d
            ) // todo: filter already existing dependencies , to only update them
            _ = log.info(s"${pom.mavenRef.name} has been inserted")
          } yield ()
        }
        .getOrElse(
          Future.successful(log.info(s"${pom.mavenRef.name} is not inserted"))
        )
    } yield ()
  }
}

case class UpdateIndex(
    repo: GithubRepo,
    pom: ReleaseModel,
    data: PublishData,
    localRepo: LocalPomRepository
)
