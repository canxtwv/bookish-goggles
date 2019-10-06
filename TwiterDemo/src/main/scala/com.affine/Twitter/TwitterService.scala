package com.affine.Twitter

import java.util.UUID

import akka.actor._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.scaladsl.{Sink, Source}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.affine.Twitter.data._
import io.surfkit.typebus
import io.surfkit.typebus._
import io.surfkit.typebus.annotations.ServiceMethod
import io.surfkit.typebus.bus.{Publisher, RetryBackoff}
import io.surfkit.typebus.event.{EventMeta, EventType, PublishedEvent, ServiceIdentifier}
import io.surfkit.typebus.module.Service

import scala.concurrent.Future
import scala.concurrent.duration._
import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.streaming.StreamingMessage
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken}

import scala.concurrent.duration._

class TwitterService(serviceIdentifier: ServiceIdentifier, publisher: Publisher, sys: ActorSystem, twitteruserDb: TwitteruserDatabase) extends Service(serviceIdentifier,publisher) with AvroByteStreams{
  implicit val system = sys
  import system.dispatcher

  val decider: Supervision.Decider = {
    case _ => Supervision.Resume  // Never give up !
  }

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

  system.log.info("Starting service: " + serviceIdentifier.name)
  val bus = publisher.busActor
  import com.affine.Twitter.data.Implicits._

  val consumerToken = ConsumerToken(key = "bol40yMDeBWz7lFlABLZo6B51", secret = "7CSILpVXrHSuc8fippiHxvrVGOwDYobJeXhbKTC0yxpaiS9IqU")
  val accessToken = AccessToken(key = "24674706-Z6CnSua0V1qvEuATRTJhCKoc36AaxuoBeT6KS2Fju", secret = "SbJf1LuD5p3dM6F9k32cCacfCUkuNXp8zPCoMxEmGby4e")

  val restClient = TwitterRestClient.withActorSystem(consumerToken, accessToken)(system)
  val streamingClient = TwitterStreamingClient.withActorSystem(consumerToken, accessToken)(system)

  var sources = Set.empty[Source[_, _]]

  val lbUser = "lightbend"

  Source.tick(5 seconds, 20 seconds, 1).mapAsync(2) { _ =>
    restClient.userTimelineForUser(lbUser, count = 1)
  }.map{ tweets =>
    println(tweets.data)
    val data =  RecentTweets(lbUser, tweets.data.map(TwitterService.toApi))
    publisher.publish(PublishedEvent(
      meta = EventMeta(
        eventId = UUID.randomUUID().toString,
        correlationId = None,
        eventType = EventType(data.getClass.getCanonicalName),
        trace = true
      ),
      payload = recentTweetsRW.write(data)
    ))
    twitteruserDb.addTweets(AddTweets(lbUser, data.tweets))
  }.runWith(Sink.ignore)


  @ServiceMethod
  def addTwitterStream(add: AddTwitterStream, meta: EventMeta): Future[TwitterStreamAdded] = {
    val user = add.user
    val s = Source.tick(5 seconds, 20 seconds, 1).mapAsync(2) { _ =>
      restClient.userTimelineForUser(user, count = add.count)
    }.map{ tweets =>
      val data =  RecentTweets(user, tweets.data.map(TwitterService.toApi))
      publisher.publish(PublishedEvent(
        meta = EventMeta(
          eventId = UUID.randomUUID().toString,
          correlationId = None,
          eventType = EventType(data.getClass.getCanonicalName),
          trace = true
        ),
        payload = recentTweetsRW.write(data)
      ))
      twitteruserDb.addTweets(AddTweets(user, data.tweets))
    }
    sources += s
    s.runWith(Sink.ignore)
    Future.successful(TwitterStreamAdded(add.user, add.count))
  }
  registerStream(addTwitterStream _)
    .withPartitionKey(_.user)


  @ServiceMethod
  def getTwitteruser(getTwitteruser: GetTwitteruserCommand, meta: EventMeta): Future[Twitteruser] = twitteruserDb.getTwitteruser(getTwitteruser)
  registerStream(getTwitteruser _)
    .withPartitionKey(_.user)

  system.log.info("Finished registering streams, trying to start service.")

}



object TwitterService{
  def toApi(t: com.danielasfregola.twitter4s.entities.Tweet): Tweet ={
    Tweet(
      favorite_count = t.favorite_count,
      favorited = t.favorited,
      filter_level = t.filter_level,
      id = t.id.toString,
      id_str = t.id_str,
      lang = t.lang,
      possibly_sensitive = t.possibly_sensitive,
      quoted_status_id = t.quoted_status_id.map(_.toString),
      quoted_status_id_str = t.quoted_status_id_str,
      scopes = t.scopes,
      retweet_count = t.retweet_count.toInt,
      retweeted = t.retweeted,
      source = t.source,
      text = t.text,
      truncated = t.truncated
    )
  }
}