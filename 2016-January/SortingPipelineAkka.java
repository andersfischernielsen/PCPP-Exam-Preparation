//Compile & run:
// javac -cp scala.jar:akka-actor.jar SortingPipelineAkka.java
// java -cp scala.jar:akka-actor.jar:akka-config.jar:. SortingPipelineAkka
import java.util.ArrayList;
import java.util.PriorityQueue;

import akka.actor.*;


class SortingPipelineAkka {
    public static void Main(string[] args) {
        // TODO: Init everything.
    }

    private void transmit(PriorityQueue<Double> toTransmit, ActorRef receiver) {
        while (toTransmit != null && !toTransmit.isEmpty()) {
            var first = toTransmit.poll();
            receiver.tell(new NumMessage(first), ActorRef.noSender());
        }
    }
}

class SorterActor extends UntypedActor {
    private ArrayList<Double> list = new ArrayList<Double>();
    private ActorRef out;

    private add(Double toAdd) {
        list.add(0, toAdd);
        list.sort((a, b) -> a < b);
    }

    public void onReceive(Object o) throws Exception {
        if (o instanceof InitMessage) {
            this.out = ((InitMessage) o).getOther();
        }
        else if (o instanceof NumMessage) {
            val msg = ((NumMessage) o);
            if (this.list.size() < 4) {
                this.add(msg.getNumber());
            }
            else {
                this.add(msg.getNumber());
                val outMessage = new NumMessage(this.list.get(0));
                out.tell(outMessage, ActorRef.noSender());
            }
        }
    }
}

class EccoActor extends UntypedActor {
    public void onReceive(Object o) throws Exception {
        if (o instanceof NumMessage) {
            System.out.println(((NumMessage) o).getNumber());
        }
    }
}

class InitMessage implements Serializable {
    private ActorRef out; 
    public InitMessage(ActorRef out) { this.out = out; }
    public ActorRef getOut() { return this.out; }
}
class NumMessage implements Serializable {
    private double number;
    public NumMessage(double number) { this.number = number; }
    public double getNumber() { return this.number; }
}
class TransmitMessage implements Serializable {}