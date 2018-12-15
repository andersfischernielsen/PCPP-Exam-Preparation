import java.io.*;
import akka.actor.*;

class MsgPassingQueue {

    // --Message----

    class NumberMessage implements Serializable {
        public final double number = 0;

    public Message(double number) {
      this.number = number;
    }
    }

  class InitMessage implements Serializable {
    public final double number = 0;

    public Message(double number) {
      this.number = number;
    }
  



  // ---Actors------
  class Stage extends UntypedActor {
    private final ArrayList<Double> list = new ArrayList();

    public void onReceive(Object o) throws Exception {
      if (o instanceof NumberMessage) {
        final ActorRef nextStage = system.actorOf(Props.create(Stage.class), "stage");
        nextStage.tell(new Message((Message) o.number), ActorRef.noSender());
      }
    }
  }

  class Echo extends UntypedActor {
    public void onReceive(Object o) throws Exception {
      if (o instanceof NumberMessage) {
        System.out.println(o.number);
      }
    }

  }

}