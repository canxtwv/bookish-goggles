package com.affine.TwitterDemo

import java.util.UUID
import io.surfkit.typebus._
import io.surfkit.typebus.event.DbAccessor
import io.surfkit.typebus.entity.EntityDb
import scala.concurrent.Future

package object data {


  sealed trait TwitterdemoCommand
  case class CreateTwitterdemo(data: String) extends TwitterdemoCommand
  case class GetTwitterdemo(id: UUID) extends TwitterdemoCommand
  case class GetTwitterdemoEntityState(id: String) extends TwitterdemoCommand with DbAccessor

  sealed trait TwitterdemoEvent
  case class TwitterdemoCreated(entity: Twitterdemo) extends TwitterdemoEvent
  case class TwitterdemoCompensatingActionPerformed(state: TwitterdemoState) extends TwitterdemoEvent
  case class TwitterdemoState(entity: Option[Twitterdemo])
  case class Twitterdemo(id: UUID, data: String)

  object Implicits extends AvroByteStreams{
    implicit val createTwitterdemoRW = Typebus.declareType[CreateTwitterdemo, AvroByteStreamReader[CreateTwitterdemo], AvroByteStreamWriter[CreateTwitterdemo]]
    implicit val TwitterdemoCreatedRW = Typebus.declareType[TwitterdemoCreated, AvroByteStreamReader[TwitterdemoCreated], AvroByteStreamWriter[TwitterdemoCreated]]
    implicit val TwitterdemoRW = Typebus.declareType[Twitterdemo, AvroByteStreamReader[Twitterdemo], AvroByteStreamWriter[Twitterdemo]]
    implicit val getTwitterdemoRW = Typebus.declareType[GetTwitterdemo, AvroByteStreamReader[GetTwitterdemo], AvroByteStreamWriter[GetTwitterdemo]]
    implicit val getTwitterdemoEntityStateRW = Typebus.declareType[GetTwitterdemoEntityState, AvroByteStreamReader[GetTwitterdemoEntityState], AvroByteStreamWriter[GetTwitterdemoEntityState]]
    implicit val TwitterdemoStateRW = Typebus.declareType[TwitterdemoState, AvroByteStreamReader[TwitterdemoState], AvroByteStreamWriter[TwitterdemoState]]
  }

  trait TwitterdemoDatabase extends EntityDb[TwitterdemoState]{
    def createTwitterdemo(x: CreateTwitterdemo): Future[TwitterdemoCreated]
    def getTwitterdemo(x: GetTwitterdemo): Future[Twitterdemo]
  }
}



