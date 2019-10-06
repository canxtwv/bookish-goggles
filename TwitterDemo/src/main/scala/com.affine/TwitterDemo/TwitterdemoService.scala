package com.affine.TwitterDemo

import akka.actor._
import com.affine.TwitterDemo.data._
import io.surfkit.typebus
import io.surfkit.typebus._
import io.surfkit.typebus.annotations.ServiceMethod
import io.surfkit.typebus.bus.{Publisher, RetryBackoff}
import io.surfkit.typebus.event.{EventMeta, ServiceIdentifier}
import io.surfkit.typebus.module.Service

import scala.concurrent.Future
import scala.concurrent.duration._

class TwitterdemoService(serviceIdentifier: ServiceIdentifier, publisher: Publisher, sys: ActorSystem, twitterdemoDb: TwitterdemoDatabase) extends Service(serviceIdentifier,publisher) with AvroByteStreams{
  implicit val system = sys

  system.log.info("Starting service: " + serviceIdentifier.name)
  val bus = publisher.busActor
  import com.affine.TwitterDemo.data.Implicits._

  registerDataBaseStream[GetTwitterdemoEntityState, TwitterdemoState](twitterdemoDb)

  @ServiceMethod
  def createTwitterdemo(createTwitterdemo: CreateTwitterdemo, meta: EventMeta): Future[TwitterdemoCreated] = twitterdemoDb.createTwitterdemo(createTwitterdemo)
  registerStream(createTwitterdemo _)
    .withPartitionKey(_.entity.id.toString)

  @ServiceMethod
  def getTwitterdemo(getTwitterdemo: GetTwitterdemo, meta: EventMeta): Future[Twitterdemo] = twitterdemoDb.getTwitterdemo(getTwitterdemo)
  registerStream(getTwitterdemo _)
    .withRetryPolicy{
    case _ => typebus.bus.RetryPolicy(3, 1 second, RetryBackoff.Exponential)
  }

  system.log.info("Finished registering streams, trying to start service.")

}