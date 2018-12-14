
// COMPILE:
// javac -cp scala.jar:akka-actor.jar SortingPipelineAkkaMorten.java
// RUN:
// java -cp scala.jar:akka-actor.jar:akka-config.jar:. SortingPipelineAkkaMorten

import java.io.Serializable;
import java.util.ArrayList;

import akka.actor.*;

class SortingActor extends UntypedActor {
    private ArrayList<Double> list = new ArrayList<Double>();
    private ActorRef out;

    public void onReceive(Object o) {
        if (o instanceof InitMessage)
            this.out = ((InitMessage) o).actor;
        else {
            NumMessage m = (NumMessage) o;
            if (list.size() < 4)
                add(m.num);
            else {
                add(m.num);
                out.tell(new NumMessage(list.remove(0)), ActorRef.noSender());
            }
        }
    }

    public void add(double d) {
        list.add(d);
        list.sort((a, b) -> Double.compare(a, b));
    }
}

class EchoActor extends UntypedActor {
    public void onReceive(Object o) {
        System.out.println(((NumMessage) o).num);
    }
}

class InitMessage implements Serializable {
    public final ActorRef actor;

    public InitMessage(ActorRef actor) {
        this.actor = actor;
    }
}

class NumMessage implements Serializable {
    public final double num;

    public NumMessage(double num) {
        this.num = num;
    }
}

/* MAIN */
public class SortingPipelineAkkaMorten {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("SortingPipeLine");
        final ActorRef first = system.actorOf(Props.create(SortingActor.class), "first");
        final ActorRef second = system.actorOf(Props.create(SortingActor.class), "second");
        final ActorRef echo = system.actorOf(Props.create(EchoActor.class), "echo");
        first.tell(new InitMessage(second), ActorRef.noSender());
        second.tell(new InitMessage(echo), ActorRef.noSender());
        double[] in = { 4.0, 7.0, 2.0, 8.0, 6.0, 1.0, 5.0, 3.0 };
        for (int i = 0; i < in.length; i++) {
            first.tell(new NumMessage(in[i]), ActorRef.noSender());
        }
        for (int i = 0; i < in.length; i++) {
            first.tell(new NumMessage(9.0), ActorRef.noSender());
        }
    }
}