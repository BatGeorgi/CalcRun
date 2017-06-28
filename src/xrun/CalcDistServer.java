package xrun;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;


public class CalcDistServer {
	
	public static void main( String[] args ) throws Exception {
      Server server = new Server(8080);
      server.setHandler(new CalcDistHandler());
      server.start();
      server.join();
  }

}

class CalcDistHandler extends AbstractHandler {

	final String greeting;
	final String body;

	public CalcDistHandler() {
		this("Hello World");
	}

	public CalcDistHandler(String greeting) {
		this(greeting, null);
	}

	public CalcDistHandler(String greeting, String body) {
		this.greeting = greeting;
		this.body = body;
	}

	public void handle(String target, Request baseRequest,
			HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		if (!"/xrun".equals(target)) {
			return;
		}
		response.setContentType("text/html; charset=utf-8");
		response.setStatus(HttpServletResponse.SC_OK);

		PrintWriter out = response.getWriter();

		out.println("<h1>" + greeting + "</h1>");
		if (body != null) {
			out.println(body);
		}

		baseRequest.setHandled(true);
	}
}
