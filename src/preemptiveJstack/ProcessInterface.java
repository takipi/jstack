package preemptiveJstack;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class ProcessInterface
{	
	private final Map<String, String> addModifyEnv;
	private final Set<String> removeEnv;
	
	static class StreamGobbler extends Thread
	{
		private static int gobblerNumber = 0;
		
		private final InputStream is;
		private final OutputStream os;
		
		private static synchronized int getNextGobblerNumber()
		{
			return gobblerNumber++;
		}
		
		public StreamGobbler(InputStream is, OutputStream os)
		{
			super("StreamGobblerThread-" + getNextGobblerNumber());
			
			this.is = is;
			this.os = os;
		}
		
		@Override
		public void run()
		{
			try
			{
				OutputStreamWriter osw = new OutputStreamWriter(os);
				BufferedWriter bw = new BufferedWriter(osw);
				
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				
				String line = null;
				
				while ((line = br.readLine()) != null)
				{
					bw.write(line);
					bw.newLine();
				}
				
				bw.flush();
				osw.flush();
			}
			catch (IOException ioe)
			{
				throw new IllegalStateException(ioe);
			}
		}
	}
	
	public ProcessInterface()
	{
		this.addModifyEnv = new HashMap<String, String>();
		this.removeEnv = new HashSet<String>();
	}
	
	public void removeEnv(String name)
	{
		if (addModifyEnv.containsKey(name))
		{
			addModifyEnv.remove(name);
		}
		
		removeEnv.add(name);
	}
	
	public void addEnv(String name, String value)
	{
		if (removeEnv.contains(name))
		{
			removeEnv.remove(name);
		}
		
		addModifyEnv.put(name, value);
	}
	
	public int run(String[] command) throws InterruptedException, IOException
	{
		return run(command, null, null, null);
	}
	
	public int run(String[] command, File workingDir) throws InterruptedException, IOException
	{
		return run(command, null, null, workingDir);
	}
	
	public int run(String[] command, OutputStream os) throws InterruptedException, IOException
	{
		return run(command, os, os, null);
	}
	
	public int run(String[] command, OutputStream os, File workingDir) throws InterruptedException, IOException
	{
		return run(command, os, os, workingDir);
	}
	
	public int run(String[] command, OutputStream std, OutputStream err, File workingDir)
			throws InterruptedException, IOException
	{
		StringBuilder sb = new StringBuilder();
		
		for (String s : command)
		{
			sb.append(s);
			sb.append(" ");
		}
		
		ProcessBuilder builder = new ProcessBuilder();
		
		if (workingDir != null)
		{
			builder.directory(workingDir);
		}
		
		Map<String, String> env = builder.environment();
		
		for (String name : removeEnv)
		{
			env.remove(name);
		}
		
		for (Entry<String, String> entry : addModifyEnv.entrySet())
		{
			env.put(entry.getKey(), entry.getValue());
		}
		
		builder.command(command);
		
		Process process = builder.start();
		
		OutputStream outStream = ((std == null) ? System.out : std);
		OutputStream errStream = ((err == null) ? System.err : err);
		
		StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), outStream);
		StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), errStream);
		
		outputGobbler.start();
		errorGobbler.start();
		
		int exitVal = process.waitFor();
		
		return exitVal;
	}
}
