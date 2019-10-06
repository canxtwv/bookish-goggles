package com.affine.Twitter.entity

import java.time.LocalDateTime

import com.lightbend.lagom.scaladsl.persistence._
import com.affine.Twitter.data._
import scala.concurrent.Future

class TwitteruserEntity extends PersistentEntity {

  override type Command = TwitteruserCommand[_]
  override type Event = TwitteruserEvent
  override type State = TwitteruserState

  override def initialState = TwitteruserState(tweets = Map.empty[String, Tweet])

  override def behavior: (TwitteruserState) => Actions = {
    case TwitteruserState(_) => Actions()
      .onCommand[AddTweets, TweetsAdded] {
        case (AddTweets(_, x), ctx, s) ⇒
          val notSeen = x.filterNot(y => s.tweets.contains(y.id))
          ctx.thenPersist(TweetsAdded(entityId, notSeen))(y ⇒ ctx.reply(y))
      }.onReadOnlyCommand[GetTwitteruserCommand, Twitteruser] {
        case (GetTwitteruserCommand(id), ctx, state) =>
          println("TwitteruserEntity::GetTwitteruserCommand")
          ctx.reply(Twitteruser(entityId, state.tweets.values.toSeq))
      }
      .onEvent {
        case (TweetsAdded(_, x), s) ⇒
          TwitteruserState(tweets = s.tweets ++ x.map(y => y.id -> y).toMap)
      }
  }
}

class TwitteruserEntityDatabase(persistentEntityRegistry: PersistentEntityRegistry) extends TwitteruserDatabase{
  def addTweets(x: AddTweets): Future[TweetsAdded] = persistentEntityRegistry.refFor[TwitteruserEntity](x.user).ask(x)
  def getTwitteruser(x: GetTwitteruserCommand): Future[Twitteruser] = persistentEntityRegistry.refFor[TwitteruserEntity](x.user).ask(x)
}
