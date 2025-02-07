package ch.epfl.scala.index.server

import ch.epfl.scala.index.model.Milestone
import ch.epfl.scala.index.model.ReleaseCandidate
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release.ScalaVersion._
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.server.BadgesSupport.SummariseLanguageVersions
import ch.epfl.scala.index.server.BadgesSupport.SummarisePlatformEditions
import org.apache.commons.lang3.StringUtils.countMatches
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class BadgesSupportTest extends AsyncFunSpec with Matchers {

  val `3.0.0-M3`: LanguageVersion = Scala3Version(
    PreReleaseBinary(3, 0, Some(0), Milestone(3))
  )
  val `3.0.0-RC2`: LanguageVersion = Scala3Version(
    PreReleaseBinary(3, 0, Some(0), ReleaseCandidate(2))
  )
  val `3.0.0-RC3`: LanguageVersion = Scala3Version(
    PreReleaseBinary(3, 0, Some(0), ReleaseCandidate(3))
  )

  val `7.0.0`: SemanticVersion = SemanticVersion(7, 0, 0)
  val `7.1.0`: SemanticVersion = SemanticVersion(7, 1, 0)
  val `7.2.0`: SemanticVersion = SemanticVersion(7, 2, 0)
  val `7.3.0`: SemanticVersion = SemanticVersion(7, 3, 0)

  it(
    "use the SummariseLanguageVersions strategy if any targets are not for platforms that fully determine the Scala version"
  ) {
    BadgesSupport.summaryOfLatestVersions(
      Map(
        `7.0.0` -> Set(
          ScalaNative(`2.13`, Native.`0.3`),
          ScalaNative(`2.13`, Native.`0.4`)
        )
      )
    ) shouldBe "7.0.0 (Scala 2.13 - Native 0.4, 0.3)"
  }

  it(
    "use the SummarisePlatformEditions strategy if *all* targets are for platforms that fully determine the Scala version"
  ) {
    BadgesSupport.summaryOfLatestVersions(
      Map(
        `7.0.0` -> Set(
          SbtPlugin(`2.12`, Sbt.`1.0`),
          SbtPlugin(`2.10`, Sbt.`0.13`)
        )
      )
    ) shouldBe "7.0.0 (sbt 1.0, 0.13)"
  }

  it("should provide a concise summary of Scala support by the artifact") {
    SummariseLanguageVersions.summarise(
      Map(
        `7.0.0` -> Set(ScalaJvm(`2.11`)),
        `7.1.0` -> Set(ScalaJvm(`2.11`), ScalaJvm(`2.12`)),
        `7.2.0` -> Set(ScalaJvm(`2.12`), ScalaJvm(`2.13`))
      )
    ) shouldBe "7.2.0 (Scala 2.13, 2.12), 7.1.0 (Scala 2.11)"
  }

  it(
    "should concisely convey which versions of Scala are supported, most recent Scala version first"
  ) {
    SummariseLanguageVersions.summarise(
      Map(
        `7.0.0` -> Set(
          ScalaJvm(`2.12`),
          ScalaJvm(`2.13`),
          ScalaJvm(`3.0.0-RC3`)
        )
      )
    ) should include(
      "Scala 3.0.0-RC3, 2.13, 2.12"
    )
  }

  it(
    "should convey the latest artifact version available for each Scala language version"
  ) {
    val summary = SummariseLanguageVersions.summarise(
      Map(
        `7.0.0` -> Set(ScalaJvm(`2.11`)),
        `7.1.0` -> Set(ScalaJvm(`2.11`)),
        `7.2.0` -> Set(ScalaJvm(`2.12`)),
        `7.3.0` -> Set(ScalaJvm(`2.12`))
      )
    )

    // these artifact versions are not the latest available support for any Scala language version, so uninteresting:
    summary should not(include("7.0.0") or include("7.2.0"))

    summary should include("7.1.0 (Scala 2.11)")
    summary should include("7.3.0 (Scala 2.12)")
  }

  it(
    "should, for brevity, not mention a Scala language version more than once, even if it occurs in multiple artifact versions being mentioned"
  ) {
    val summary = SummariseLanguageVersions.summarise(
      Map(
        `7.1.0` -> Set(ScalaJvm(`2.11`), ScalaJvm(`2.12`)),
        `7.2.0` -> Set(ScalaJvm(`2.12`))
      )
    )

    // it happens that two artifact versions that support Scala 2.12 will be mentioned...
    assert(summary.contains("7.1.0") && summary.contains("7.2.0"))

    // ...but for brevity no Scala language version (eg Scala 2.12 in this case) should be mentioned more than once...
    countMatches(summary, "2.12") shouldBe 1

    // ...specifically it should be listed  against the *latest* artifact that supports that Scala language version
    summary should include("7.2.0 (Scala 2.12)")
  }

  it(
    "should, for brevity, only mention the *latest* Scala language versions available for any given Scala binary version family"
  ) {
    SummariseLanguageVersions.summarise(
      Map(
        `7.0.0` -> Set(ScalaJvm(`2.13`), ScalaJvm(`3.0.0-M3`)),
        `7.1.0` -> Set(ScalaJvm(`2.13`), ScalaJvm(`3.0.0-RC2`)),
        `7.2.0` -> Set(ScalaJvm(`2.13`), ScalaJvm(`3.0.0-RC3`))
      )
    ) shouldBe "7.2.0 (Scala 3.0.0-RC3, 2.13)"
  }

  it(
    "should list the Scala platform editions that support all cited versions of the Scala language"
  ) {
    SummariseLanguageVersions.summarise(
      Map(
        `7.1.0` -> Set(
          ScalaNative(`3.0.0-M3`, Native.`0.3`),
          ScalaNative(`2.13`, Native.`0.3`),
          ScalaNative(`3.0.0-M3`, Native.`0.4`),
          ScalaNative(`2.13`, Native.`0.4`)
        )
      )
    ) shouldBe "7.1.0 (Scala 3.0.0-M3, 2.13 - Native 0.4, 0.3)"

  }

  it(
    "should not list Scala platform editions that are not supported by all cited versions of the Scala language"
  ) {
    SummariseLanguageVersions.summarise(
      Map(
        `7.1.0` -> Set(
          ScalaNative(`2.13`, Native.`0.3`),
          ScalaNative(`3.0.0-M3`, Native.`0.4`)
        )
      )
    ) shouldBe "7.1.0 (Scala 3.0.0-M3, 2.13)"
  }

  it(
    "should summarise *just* platform versions"
  ) {
    SummarisePlatformEditions.summarise(
      Map(
        `7.0.0` -> Set(
          SbtPlugin(`2.12`, Sbt.`1.0`),
          SbtPlugin(`2.10`, Sbt.`0.13`)
        )
      )
    ) shouldBe "7.0.0 (sbt 1.0, 0.13)"
  }

  it(
    "should convey the latest artifact version available for each sbt version"
  ) {
    val summary = SummarisePlatformEditions.summarise(
      Map(
        `7.0.0` -> Set(SbtPlugin(`2.10`, Sbt.`0.13`)),
        `7.1.0` -> Set(SbtPlugin(`2.10`, Sbt.`0.13`)),
        `7.2.0` -> Set(SbtPlugin(`2.12`, Sbt.`1.0`)),
        `7.3.0` -> Set(SbtPlugin(`2.12`, Sbt.`1.0`))
      )
    )

    // these artifact versions are not the latest available support for any sbt version, so uninteresting:
    summary should not(include("7.0.0") or include("7.2.0"))

    summary should include("7.1.0 (sbt 0.13)")
    summary should include("7.3.0 (sbt 1.0)")
  }

  it(
    "should, for brevity, not mention a sbt version more than once, even if it occurs in multiple artifact versions being mentioned"
  ) {
    val summary = SummarisePlatformEditions.summarise(
      Map(
        `7.3.0` -> Set(
          SbtPlugin(`2.12`, Sbt.`1.0`)
        ),
        `7.2.0` -> Set(
          SbtPlugin(`2.12`, Sbt.`1.0`),
          SbtPlugin(`2.10`, Sbt.`0.13`)
        )
      )
    )

    // it happens that two artifact versions that support sbt 1.0 will be mentioned...
    assert(summary.contains("7.2.0") && summary.contains("7.3.0"))

    // ...but for brevity no sbt version (eg sbt 1.0 in this case) should be mentioned more than once...
    countMatches(summary, "1.0") shouldBe 1

    // ...specifically it should be listed  against the *latest* artifact that supports that Scala language version
    summary should include("7.3.0 (sbt 1.0)")
  }
}
