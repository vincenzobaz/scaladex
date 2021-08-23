package ch.epfl.scala.utils

import scala.concurrent.ExecutionContext
import scala.util.Try

import cats.effect.ContextShift
import cats.effect.IO
import ch.epfl.scala.index.model.License
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.misc.GithubContributor
import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.model.misc.Url
import ch.epfl.scala.index.model.release.Resolver
import ch.epfl.scala.index.model.release.ScalaTarget
import ch.epfl.scala.index.newModel.NewDependency
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.DocumentationLink
import ch.epfl.scala.index.newModel.NewProject.Organization
import ch.epfl.scala.index.newModel.NewProject.Repository
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.services.storage.sql.DbConf
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import doobie._
import doobie.implicits._
import doobie.util.Read
import doobie.util.Write
import doobie.util.fragment.Fragment
import doobie.util.meta.Meta
import io.circe._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import org.flywaydb.core.Flyway
import org.joda.time.DateTime

object DoobieUtils {
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  def create(conf: DbConf): (doobie.Transactor[IO], Flyway) = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    val xa: doobie.Transactor[IO] = conf match {
      case c: DbConf.H2 =>
        Transactor.fromDriverManager[IO](c.driver, c.url, "", "")
      case c: DbConf.PostgreSQL =>
        Transactor.fromDriverManager[IO](c.driver, c.url, c.user, c.pass.decode)
    }

    val config = new HikariConfig()
    conf match {
      case c: DbConf.H2 =>
        config.setDriverClassName(c.driver)
        config.setJdbcUrl(c.url)
      case c: DbConf.PostgreSQL =>
        config.setDriverClassName(c.driver)
        config.setJdbcUrl(c.url)
        config.setUsername(c.user)
        config.setPassword(c.pass.decode)
    }
    val flyway = Flyway
      .configure()
      .dataSource(new HikariDataSource(config))
      .locations("classpath:migrations")
      .load()

    (xa, flyway)
  }

  object Fragments {
    val empty: Fragment = fr0""
    val space: Fragment = fr0" "

    def buildInsert(
        table: Fragment,
        fields: Fragment,
        values: Fragment
    ): Fragment =
      fr"INSERT INTO" ++ table ++ fr0" (" ++ fields ++ fr0") VALUES (" ++ values ++ fr0")"

    def buildSelect(table: Fragment, fields: Fragment): Fragment =
      fr"SELECT" ++ fields ++ fr" FROM" ++ table

    def buildSelect(
        table: Fragment,
        fields: Fragment,
        where: Fragment
    ): Fragment =
      buildSelect(table, fields) ++ space ++ where
  }

  object Mappings {
    implicit val stringMeta: Meta[String] = Meta.StringMeta
    implicit val URLDecoder: Decoder[Url] = Decoder.decodeString.map(Url)
    implicit val URLEncoder: Encoder[Url] =
      Encoder.encodeString.contramap[Url](_.target)
    implicit val contributorMeta: Meta[List[GithubContributor]] = {
      implicit val contributorDecoder: Decoder[GithubContributor] =
        deriveDecoder[GithubContributor]
      implicit val contributorEncoder: Encoder[GithubContributor] =
        deriveEncoder[GithubContributor]
      stringMeta.timap(fromJson[List[GithubContributor]](_).get)(toJson(_))
    }
    implicit val githubIssuesMeta: Meta[List[GithubIssue]] = {
      implicit val githubIssueDecoder: Decoder[GithubIssue] =
        deriveDecoder[GithubIssue]
      implicit val githubIssueEncoder: Encoder[GithubIssue] =
        deriveEncoder[GithubIssue]
      stringMeta.timap(fromJson[List[GithubIssue]](_).get)(toJson(_))
    }
    implicit val documentationLinksMeta: Meta[List[DocumentationLink]] = {
      implicit val documentationDecoder: Decoder[DocumentationLink] =
        deriveDecoder[DocumentationLink]
      implicit val documentationEncoder: Encoder[DocumentationLink] =
        deriveEncoder[DocumentationLink]
      stringMeta.timap(fromJson[List[DocumentationLink]](_).get)(toJson(_))
    }
    implicit val topicsMeta: Meta[Set[String]] = {
      stringMeta.timap(_.split(",").filter(_.nonEmpty).toSet)(
        _.mkString(",")
      )
    }
    implicit val semanticVersionMeta: Meta[SemanticVersion] =
      stringMeta.timap(SemanticVersion.tryParse(_).get)(_.toString)
    implicit val scalaTargetMeta: Meta[ScalaTarget] =
      stringMeta.timap(ScalaTarget.parse(_).get)(_.render)
    implicit val licensesMeta: Meta[Set[License]] = {
      implicit val licenseDecoder: Decoder[License] =
        deriveDecoder[License]
      implicit val licenseEncoder: Encoder[License] =
        deriveEncoder[License]
      stringMeta.timap(fromJson[List[License]](_).get.toSet)(toJson(_))
    }
    implicit val resolverMeta: Meta[Resolver] =
      stringMeta.timap(Resolver.from(_).get)(_.name)
    implicit val dateFormatMeta: Meta[DateTime] = stringMeta.timap(
      NewRelease.format.parseDateTime
    )(NewRelease.format.print(_))
    implicit val releaseWriter: Write[NewRelease] =
      Write[
        (
            String,
            String,
            SemanticVersion,
            Organization,
            Repository,
            ArtifactName,
            Option[ScalaTarget],
            Option[String],
            Option[DateTime],
            Option[Resolver],
            Set[License],
            Boolean
        )
      ].contramap { r =>
        (
          r.maven.groupId,
          r.maven.artifactId,
          r.version,
          r.organization,
          r.repository,
          r.artifact,
          r.target,
          r.description,
          r.released,
          r.resolver,
          r.licenses,
          r.isNonStandardLib
        )
      }

    implicit val dependencyWriter: Write[NewDependency] =
      Write[(String, String, String, String, String, String, String)]
        .contramap { d =>
          (
            d.source.groupId,
            d.source.artifactId,
            d.source.version,
            d.target.groupId,
            d.target.artifactId,
            d.target.version,
            d.scope
          )
        }

    implicit val projectReader: Read[NewProject] = Read[
      (
          Organization,
          Repository,
          Option[String]
      )
    ]
      .map {
        case (
              organization,
              repository,
              esId
            ) =>
          NewProject(
            organization = organization,
            repository = repository,
            githubInfo = None,
            esId = esId,
            formData = NewProject.FormData.default
          )
      }

    private def toJson[A](v: A)(implicit e: Encoder[A]): String =
      e.apply(v).noSpaces

    private def fromJson[A](s: String)(implicit d: Decoder[A]): Try[A] =
      parser.parse(s).flatMap(d.decodeJson).toTry
  }

}