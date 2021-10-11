package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewDependency
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.DataForm
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName

trait DatabaseApi {
  def insertProject(p: NewProject): Future[Unit]
  def insertOrUpdateProject(p: NewProject): Future[Unit]
  def updateProjectForm(
      ref: Project.Reference,
      dataForm: DataForm
  ): Future[Unit]
  def findProject(projectRef: Project.Reference): Future[Option[NewProject]]
  def insertReleases(r: Seq[NewRelease]): Future[Int]
  def findReleases(projectRef: Project.Reference): Future[Seq[NewRelease]]
  def findReleases(
      projectRef: Project.Reference,
      artifactName: ArtifactName
  ): Future[Seq[NewRelease]]

  def findDirectDependencies(
      release: NewRelease
  ): Future[List[NewDependency.Direct]]
  def findReverseDependencies(
      release: NewRelease
  ): Future[List[NewDependency.Reverse]]
  def getAllTopics(): Future[Seq[String]]
  def getAllPlatforms(): Future[Map[Project.Reference, Set[Platform]]]

  def insertDependencies(dependencies: Seq[NewDependency]): Future[Int]
  def countProjects(): Future[Long]
  def countReleases(): Future[Long]
  def countDependencies(): Future[Long]
}