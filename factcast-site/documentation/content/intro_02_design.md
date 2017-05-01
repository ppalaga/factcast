+++
draft = false
title = "Conceptual Design"
description = ""

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "intro"
identifier = "design"
weight = 20
+++

## Write (publish)

With FactCast, you write Facts into a log by *publishing* Facts. You can publish single Facts, as well as a List of Facts atomically (all-or-none). 

In order to coordinate with consumers, you can also add special *MarkFacts* at the end of the List, that you can reference from consumers lateron. 
{{%alert danger%}} TODO see markFacts {{% /alert%}}

## Read (subscribe)

In order to receive Facts, you subscribe to FactCast with a subscription request. This is where FactCast differs significantly from other solutions because the subscriptioon request contains the *full specification* of what events to receive.
This means, there is no need for Server-Side administration or knowing ahead of time, which Streams to publich the Fact to.

{{%alert danger%}} TODO see SubscriptionRequest {{% /alert%}}

Next to the specification of what kinds of events to read, the SubscriptionRequest also contains the information of which Events to skip (due to being already received by the consumer) and how to deal with Facts being published in the Future.
When subscribing, the Consumer sends a specification of Facts he is interested in and might have received Facts in the past.


#### {{% alert theme="info" %}} *Note, that Facts are always guaranteed to be sent in the order published.* {{% /alert %}}



The three usual subscription Models and their corresponding UseCases are:

| Subscription Type | Description |
|:--|:--|
| Follow | This is the 80% UseCase for consumers. Here the Consumer does catch-up with Facts from the past and (after that) receives future Facts *as they are published*. <p>On subscribing, the consumer sends the 'id' of the last event processed and gets every Fact that matches his specification, that has been published *after* this last known Fact.</p>|
| Catchup | <p>This subscription differs from Follow by completing, once the consumer has read the last of the currently published Facts.</p> <p>A usual Usecase for this kind of subscription is a write model, that needs to aggregate all information about a specific aggregate, in order to validate or reject an incoming command.</p>|
| Ephemeral | The consumer *does not catch up* with Events that happened in the past, but only receives matching Facts *from now on*. <p>A good UseCase is cache invalidation, but certainly not building Read Models.</p> |

Obviously all these subscription types rely on streaming transport which is implemented (at the time of writing) by REST-SSE or GRPC.

## Read (fetchById)

There are situation, where either the bandwidth consumption has to be minimized between the consumers and FactCast, and there are either many consumers interested in the same Fact, or consumers repeatedly receiving the same Fact (Catchup-Subscriptions without snapshotting for example). 

What could help here, is not actually pushing Facts to the client, but **just 'ids' (or URLs)** to identify Facts and provide a way to fetch the Facts by that 'id' lateron. This way, we can make use of HTTP-Proxies, 'local' caches etc, depending on the protocol used.