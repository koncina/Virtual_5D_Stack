import java.io.PrintStream;
import java.io.ByteArrayOutputStream;

// From the post: http://stackoverflow.com/questions/8708342/redirect-console-output-to-string-in-java

class ConsoleOutputCapturer {
	private ByteArrayOutputStream baos;
	private PrintStream oldOut, oldErr;
	private boolean capturing;

	public void start() {
		if (capturing) {
			return;
		}
		capturing = true;
		oldOut = System.out;
		oldErr = System.err;
		baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		System.setOut(ps);
		System.setErr(ps);
	}

	public void stop() {
		if (!capturing) {
			return;
		}
		System.setOut(oldOut);
		System.setErr(oldErr);
		baos = null;
		oldOut = null;
		oldErr = null;
		capturing = false;
	}
}
