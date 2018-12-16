
// COMPILE:
//javac -cp lib/scala.jar:lib/akka-actor.jar MsgQueueAkka.java
// RUN:
// java -cp lib/scala.jar:lib/akka-actor.jar:lib/akka-config.jar:. MsgQueueAkka

import java.io.*;
import java.util.ArrayList;
import java.util.PriorityQueue;
import akka.actor.*;

class MsgQueueAkka {
    public static void main(String[] args) {
        // PriorityQueue<Double> queue = new PriorityQueue<>();
        // for (int i = 0; i < 8; i++) {
        // queue.add(e);
        // }

        double[] in = { 4.0, 7.0, 2.0, 8.0, 6.0, 1.0, 5.0, 3.0 };

        final ActorSystem system = ActorSystem.create("MsgPassingQueueSystem");

        final ActorRef first = system.actorOf(Props.create(Stage.class), "first");
        final ActorRef second = system.actorOf(Props.create(Stage.class), "second");
        final ActorRef echo = system.actorOf(Props.create(Echo.class), "echo");

        first.tell(new InitMessage(second), ActorRef.noSender());
        second.tell(new InitMessage(echo), ActorRef.noSender());

        for (int i = 0; i < in.length; i++) {
            first.tell(new NumberMessage(in[i]), ActorRef.noSender());
        }

        for (int i = 0; i < in.length; i++) {
            first.tell(new NumberMessage(9), ActorRef.noSender());
        }
    }

}

// --Message----
class NumberMessage implements Serializable {
    public double number = 0;

    public NumberMessage(double number) {
        this.number = number;
    }
}

class InitMessage implements Serializable {

    public final ActorRef ref;

    public InitMessage(ActorRef ref) {
        this.ref = ref;
    }
}

// ---Actors------
class Stage extends UntypedActor {
    private final ArrayList<Double> list = new ArrayList(4);
    private ActorRef out;

    private void add(double d) {
        list.add(d);
        list.sort((a, b) -> Double.compare(a, b));
    }

    public void onReceive(Object o) throws Exception {
        if (o instanceof InitMessage) {
            this.out = ((InitMessage) o).ref;
        }
        if (o instanceof NumberMessage) {
            NumberMessage numMsg = (NumberMessage) o;

            if (list.size() < 4) {
                add(((NumberMessage) o).number);
            } else if (numMsg.number < list.get(0)) {
                out.tell(new NumberMessage(numMsg.number), ActorRef.noSender());
            } else {
                double toForward = list.remove(0);
                add(numMsg.number);
                out.tell(new NumberMessage(toForward), ActorRef.noSender());
            }
        }
    }
}

class Echo extends UntypedActor {
    public void onReceive(Object o) throws Exception {
        if (o instanceof NumberMessage) {
            NumberMessage numMsg = (NumberMessage) o;
            System.out.println(numMsg.number);
        }
    }

}
