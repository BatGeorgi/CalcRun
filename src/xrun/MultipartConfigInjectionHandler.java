package xrun;

import java.io.IOException;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.MultiPartInputStreamParser;

public class MultipartConfigInjectionHandler extends HandlerWrapper {
  
  private static final String MULTIPART_FORMDATA_TYPE = "multipart/form-data";

  private static final MultipartConfigElement MULTI_PART_CONFIG = new MultipartConfigElement(
      System.getProperty("java.io.tmpdir"));

  public static boolean isMultipartRequest(ServletRequest request) {
    return request.getContentType() != null
        && request.getContentType().startsWith(MULTIPART_FORMDATA_TYPE);
  }

  public static void enableMultipartSupport(HttpServletRequest request) {
    request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, MULTI_PART_CONFIG);
  }

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request,
      HttpServletResponse response) throws IOException, ServletException {
    boolean multipartRequest = HttpMethod.POST.is(request.getMethod())
        && isMultipartRequest(request);
    if (multipartRequest) {
      enableMultipartSupport(request);
    }

    try {
      super.handle(target, baseRequest, request, response);
    } finally {
      if (multipartRequest) {
        MultiPartInputStreamParser multipartInputStream = (MultiPartInputStreamParser) request
            .getAttribute(Request.__MULTIPART_INPUT_STREAM);
        if (multipartInputStream != null) {
          try {
            // a multipart request to a servlet will have the parts cleaned up correctly, but
            // the repeated call to deleteParts() here will safely do nothing.
            multipartInputStream.deleteParts();
          } catch (MultiException e) {
//            LOG error("Error while deleting multipart request parts", e);
          }
        }
      }
    }
  }
}