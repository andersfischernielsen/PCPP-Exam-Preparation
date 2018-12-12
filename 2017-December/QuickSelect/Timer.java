// Crude wall clock timing utility, measuring time in seconds
   
class Timer {
  private long start = 0, spent = 0;
  public Timer() { play(); }
  public double check() { return (start==0 ? spent : System.nanoTime()-start+spent)/1e9; }
  public void pause() { if (start != 0) { spent += System.nanoTime()-start; start = 0; } }
  public void play() { if (start == 0) start = System.nanoTime(); }
}
