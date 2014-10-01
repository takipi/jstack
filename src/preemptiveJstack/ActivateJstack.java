package preemptiveJstack;

import java.lang.management.ManagementFactory;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class ActivateJstack {

	private static final int MIN_THROUGHPUT = 1000;
	
	private static final int APP_WARMUP = 10;
	private static final int POLLING_CYCLE = 10;
		
	public static class ExecuteJStackTask {
	   				
		private static final char PID_SEPERATOR = '@';
		
		private final String pathToJStack;
		private final String pid;
		private final ScheduledExecutorService scheduler;
		private final LongAdder adder;
		
		private static String acquirePid()
		{
			String mxName = ManagementFactory.getRuntimeMXBean().getName();
			
			int index = mxName.indexOf(PID_SEPERATOR);

			String result;

			if (index != -1) {
				result = mxName.substring(0, index);
			} else {
				throw new IllegalStateException("Could not acquire pid using " + mxName);
			}
			
			return result;
		}
		
		private void executeJstack( )
		{
			ProcessInterface pi = new ProcessInterface();

			int exitCode;

			try {
				exitCode = pi.run(new String[] { pathToJStack, "-l", pid,}, System.err);
			} catch (Exception e) {
				throw new IllegalStateException("Error invoking jstack", e);
			}

			if (exitCode != 0) {
				throw new IllegalStateException("Bad jstack exit code " + exitCode);
			}
		}
		
		public ExecuteJStackTask(String pathToJStack, int througput)
		{
			this.pathToJStack = pathToJStack;
			this.pid = acquirePid();
			this.adder = new LongAdder();
			this.scheduler = Executors.newScheduledThreadPool(1);
			this.adder.add(througput);
		}
			
	    public void startScheduleTask() {
	  
		    scheduler.scheduleAtFixedRate(new Runnable() {
		    	public void run() {
		              
		    		checkThroughput();
		    		
		            }
		        }, APP_WARMUP, POLLING_CYCLE, TimeUnit.SECONDS);
	    }
	    
	    private void checkThroughput()
	    {
	    	int value = adder.intValue();
	    	
	    	if (value < MIN_THROUGHPUT) {	
				Thread.currentThread().setName("Throughput thread: " + value);
	    		System.err.println("Minimal throughput failed: exexuting jstack");
				executeJstack();
			}	
	    	
	    	adder.reset();
	    }
	    
	    public void incThrughput(int val) {
	    	adder.add(val);
	    }
	}

	public static void main(String[] args) {

		System.out.println("Up");
					
		ExecuteJStackTask ste = new ExecuteJStackTask(args[0], MIN_THROUGHPUT);
		ste.startScheduleTask();
		
		Scanner scanner = new Scanner(System.in);
		
		try
		{
			while (true)
			{		
				int throughput = scanner.nextInt();
				ste.incThrughput(throughput);
				System.out.println("Througput = " + throughput);
			}
		}
		finally
		{
			scanner.close();
		}
	}
}





