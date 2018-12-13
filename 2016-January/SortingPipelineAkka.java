//Compile & run:
// javac -cp scala.jar:akka-actor.jar SortingPipelineAkka.java
// java -cp scala.jar:akka-actor.jar:akka-config.jar:. SortingPipelineAkka
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import akka.actor.*;

class SortingPipelineAkka {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("SortingPipelineSystem");
        final ActorRef first = system.actorOf(Props.create(SorterActor.class), "First");
        final ActorRef second = system.actorOf(Props.create(SorterActor.class), "Second");
        final ActorRef echo = system.actorOf(Props.create(EchoActor.class), "Echo");

        first.tell(new InitMessage(second), ActorRef.noSender());
        second.tell(new InitMessage(echo), ActorRef.noSender());
        
        var l1 = new ArrayList<>(Arrays.asList(4.0,7.0,2.0,8.0,6.0,1.0,5.0,3.0));
        var l2 = new ArrayList<>(Arrays.asList(9.0,9.0,9.0,9.0,9.0,9.0,9.0,9.0));

        transmit(l1, first);
        transmit(l2, first);
    }

    private static void transmit(ArrayList<Double> toTransmit, ActorRef receiver) {
        var t_ = (ArrayList<Double>) toTransmit.clone();
        while (t_ != null && !t_.isEmpty()) {
            receiver.tell(new NumMessage(t_.remove(0)), ActorRef.noSender());
        }
    }
}

class SorterActor extends UntypedActor {
    private ArrayList<Double> list = new ArrayList<Double>();
    private ActorRef out;

    private void add(Double toAdd) {
        list.add(0, toAdd);
        list.sort((a, b) -> a.compareTo(b));
    }

    public void onReceive(Object o) throws Exception {
        if (o instanceof InitMessage) {
            this.out = ((InitMessage) o).getOut();
        }
        else if (o instanceof NumMessage) {
            var msg = ((NumMessage) o);
            if (this.list.size() < 4) {
                this.add(msg.getNumber());
            }
            else {
                this.add(msg.getNumber());
                var outMessage = new NumMessage(this.list.remove(0));
                out.tell(outMessage, ActorRef.noSender());
            }
        }
    }
}

class EchoActor extends UntypedActor {
    public void onReceive(Object o) throws Exception {
        if (o instanceof NumMessage) {
            System.out.println(((NumMessage) o).getNumber());
        }
    }
}

class InitMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private ActorRef out;
    public InitMessage(ActorRef out) { this.out = out; }
    public ActorRef getOut() { return this.out; }
}
class NumMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private double number;
    public NumMessage(double number) { this.number = number; }
    public double getNumber() { return this.number; }
}
class TransmitMessage implements Serializable {
    private static final long serialVersionUID = 1L;
}