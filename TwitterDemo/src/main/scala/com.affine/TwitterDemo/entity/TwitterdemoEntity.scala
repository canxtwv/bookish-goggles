package com.affine.TwitterDemo.entity

import java.time.LocalDateTime
import java.util.UUID
import com.affine.TwitterDemo.data._
import scala.concurrent.Future

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import io.surfkit.typebus.bus.Publisher
import io.surfkit.typebus.entity.EntityDb

object TwitterdemoEntity {

  sealed trait Command
  // command
  final case class EntityCreateTwitterdemo(id: UUID, create: CreateTwitterdemo)(val replyTo: ActorRef[TwitterdemoCreated]) extends Command
  final case class EntityModifyState(state: TwitterdemoState)(val replyTo: ActorRef[TwitterdemoCompensatingActionPerformed]) extends Command
  // query
  final case class EntityGetTwitterdemo(get: GetTwitterdemo)(val replyTo: ActorRef[Twitterdemo]) extends Command
  final case class EntityGetState(id: UUID)(val replyTo: ActorRef[TwitterdemoState]) extends Command

  val entityTypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("TwitterdemoEntity")

  def behavior(entityId: String): Behavior[Command] =
    EventSourcedBehavior[Command, TwitterdemoEvent, TwitterdemoState](
    persistenceId = PersistenceId(entityId),
    emptyState =  TwitterdemoState(None),
    commandHandler,
    eventHandler)

  private val commandHandler: (TwitterdemoState, Command) => Effect[TwitterdemoEvent, TwitterdemoState] = { (state, command) =>
    command match {
      case x: EntityCreateTwitterdemo =>
        val id = x.id
        val entity = Twitterdemo(id, x.create.data)
        val created = TwitterdemoCreated(entity)
        Effect.persist(created).thenRun(_ => x.replyTo.tell(created))

      case x: EntityGetTwitterdemo =>
        state.entity.map(x.replyTo.tell)
        Effect.none

      case x: EntityGetState =>
        x.replyTo.tell(state)
        Effect.none

      case x: EntityModifyState =>
        val compensatingActionPerformed = TwitterdemoCompensatingActionPerformed(x.state)
        Effect.persist(compensatingActionPerformed).thenRun(_ => x.replyTo.tell(compensatingActionPerformed))

      case _ => Effect.unhandled
    }
  }

  private val eventHandler: (TwitterdemoState, TwitterdemoEvent) => TwitterdemoState = { (state, event) =>
    state match {
      case state: TwitterdemoState =>
        event match {
        case TwitterdemoCreated(module) =>
          TwitterdemoState(Some(module))
        case TwitterdemoCompensatingActionPerformed(newState) =>
          newState
        case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
      }
      case _ => throw new IllegalStateException(s"unexpected event [$event] in state [$state]")
    }
  }

}


class TwitterdemoEntityDatabase(system: ActorSystem[_], val producer: Publisher)(implicit val ex: ExecutionContext)
  extends TwitterdemoDatabase{
  import akka.util.Timeout
  import scala.concurrent.duration._
  import akka.actor.typed.scaladsl.adapter._
  private implicit val askTimeout: Timeout = Timeout(5.seconds)

  override def typeKey = TwitterdemoEntity.entityTypeKey
  val sharding =  ClusterSharding(system)
  val psEntities: ActorRef[ShardingEnvelope[TwitterdemoEntity.Command]] =
    sharding.init(Entity(typeKey = typeKey,
      createBehavior = createEntity(TwitterdemoEntity.behavior)(system.toUntyped))
      .withSettings(ClusterShardingSettings(system)))

  def entity(id: String) =
    sharding.entityRefFor(TwitterdemoEntity.entityTypeKey, id)

  override def createTwitterdemo(x: CreateTwitterdemo): Future[TwitterdemoCreated] = {
    val id = UUID.randomUUID()
    entity(id.toString) ? TwitterdemoEntity.EntityCreateTwitterdemo(id, x)
  }

  override def getTwitterdemo(x: GetTwitterdemo): Future[Twitterdemo] =
    entity(x.id.toString) ? TwitterdemoEntity.EntityGetTwitterdemo(x)

  override def getState(id: String): Future[TwitterdemoState] =
    entity(id) ? TwitterdemoEntity.EntityGetState(UUID.fromString(id))

  override def modifyState(id: String, state: TwitterdemoState): Future[TwitterdemoState] =
    (entity(id) ? TwitterdemoEntity.EntityModifyState(state)).map(_.state)
}
