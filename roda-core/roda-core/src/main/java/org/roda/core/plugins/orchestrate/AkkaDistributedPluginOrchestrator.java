/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.orchestrate;

import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.roda.core.index.IndexService;
import org.roda.core.model.ModelService;
import org.roda.core.plugins.orchestrate.akka.Frontend;
import org.roda.core.plugins.orchestrate.akka.Master;
import org.roda.core.storage.StorageService;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorIdentity;
import akka.actor.ActorPath;
import akka.actor.ActorPaths;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Identify;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.dispatch.OnFailure;
import akka.dispatch.OnSuccess;
import akka.pattern.Patterns;
import akka.persistence.journal.leveldb.SharedLeveldbJournal;
import akka.persistence.journal.leveldb.SharedLeveldbStore;
import akka.util.Timeout;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

/*
 * Based on 
 * > http://www.typesafe.com/activator/template/akka-distributed-workers
 * > https://github.com/typesafehub/activator-akka-distributed-workers-java 
 * */
public class AkkaDistributedPluginOrchestrator extends AkkaDistributedPlugin {
  private final Logger logger = Logger.getLogger(getClass());

  private final IndexService index;
  private final ModelService model;
  private final StorageService storage;
  private final ActorSystem clusterSystem;
  private final ActorRef frontend;

  public AkkaDistributedPluginOrchestrator(String hostname, String port) {
    super();
    index = getIndex();
    model = getModel();
    storage = getStorage();

    String role = "backend";
    String systemName = "ClusterSystem";
    String systemPath = systemName + "@" + hostname + ":" + port;
    Config conf = ConfigFactory.parseString("akka.cluster.roles=[" + role + "]")
      .withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=[\"akka.tcp://" + systemPath + "\"]"))
      .withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.hostname=" + hostname))
      .withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)).withFallback(ConfigFactory.load());

    clusterSystem = ActorSystem.create(systemName, conf);

    startupSharedJournal(getClusterSystem(), true, ActorPaths.fromString("akka.tcp://" + systemPath + "/user/store"));

    FiniteDuration workTimeout = Duration.create(10, "seconds");
    getClusterSystem().actorOf(ClusterSingletonManager.props(Master.props(workTimeout), PoisonPill.getInstance(),
      ClusterSingletonManagerSettings.create(getClusterSystem()).withRole(role)), "master");

    frontend = clusterSystem.actorOf(Props.create(Frontend.class), "frontend");
  }

  private static void startupSharedJournal(final ActorSystem system, boolean startStore, final ActorPath path) {
    // Start the shared journal one one node (don't crash this SPOF)
    // This will not be needed with a distributed journal
    if (startStore) {
      system.actorOf(Props.create(SharedLeveldbStore.class), "store");
    }
    // register the shared journal

    Timeout timeout = new Timeout(15, TimeUnit.SECONDS);

    ActorSelection actorSelection = system.actorSelection(path);
    Future<Object> f = Patterns.ask(actorSelection, new Identify(null), timeout);

    f.onSuccess(new OnSuccess<Object>() {

      @Override
      public void onSuccess(Object arg0) throws Throwable {
        if (arg0 instanceof ActorIdentity && ((ActorIdentity) arg0).getRef() != null) {
          SharedLeveldbJournal.setStore(((ActorIdentity) arg0).getRef(), system);
        } else {
          system.log().error("Lookup of shared journal at {} timed out", path);
          System.exit(-1);
        }

      }
    }, system.dispatcher());

    f.onFailure(new OnFailure() {
      public void onFailure(Throwable ex) throws Throwable {
        system.log().error(ex, "Lookup of shared journal at {} timed out", path);
      }
    }, system.dispatcher());
  }

  public ActorSystem getClusterSystem() {
    return clusterSystem;
  }

  public ActorRef getFrontend() {
    return frontend;
  }

}