# Rincewind

'Famous' wizard of [Unseen University](https://en.wikipedia.org/wiki/Unseen_University), or a multi-agent show case of [Akka remoting](http://doc.akka.io/docs/akka/2.3.9/scala/remoting.html).

![Rincewind](https://upload.wikimedia.org/wikipedia/en/c/c5/Rincewind.png)

## Overall

There are 3 kinds of agents, implemented by distinct actor systems communicating by Akka remoting (TCP with different ports): [server](./server), [reader](./reader) and [writer](./writer).

For each agent, a configuration file can be found in corresponding `src/main/resources/application.conf`.

There is order to be enforced for starting instances of these different agents.

## Common pre-requisites

- JDK 1.7+
- SBT 0.13.x

[Travis](https://travis-ci.org/cchantep/rincewind): ![Travis build status](https://travis-ci.org/cchantep/rincewind.svg?branch=master)

## Server

To start a server using SBT from the CLI in this project base directory: `/path/to/sbt ';project server ;run'`

## Reader

To start a reader using SBT from the CLI in this project base directory: `/path/to/sbt ';project reader ;run'`

## Writer

To start a writer using SBT from the CLI in this project base directory: `/path/to/sbt ';project writer ;run'`