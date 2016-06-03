package ch.epfl.scala.index

package object api {
  type PageIndex = Int
}

package api {
  import model.{Project, Artifact}
  import scala.concurrent.Future

  case class UserInfo(login: String, name: String, avatarUrl: String) {
    def sizedAvatarUrl(size: Int) = avatarUrl + "&s" + size.toString
  }

  case class Pagination(current: PageIndex, total: Int)

  trait Api {
    def userInfo(): Option[UserInfo]
    def find(q: String, page: PageIndex): Future[(Pagination, List[Project])]
    def projectPage(artifact: Artifact.Reference): Future[Option[Project]]
  }
}
