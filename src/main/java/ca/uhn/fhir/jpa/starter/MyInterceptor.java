package ca.uhn.fhir.jpa.starter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.HookParams;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.ResponseDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;

@Interceptor
public class MyInterceptor {
   private final static Logger logger = LoggerFactory.getLogger(MyInterceptor.class);

   private JpaRestfulServer theServer = null;
   private AppProperties theProperties = null;

   private RequestDetails theReqDetails = null;
   private ResponseDetails theResDetails = null;
   private HttpServletRequest theRequest = null;
   private HttpServletResponse theResponse = null;
   private IBaseResource theResource = null;
   private IBaseResource theReqResource = null;
   private String theRequestBody = null;

   public MyInterceptor(JpaRestfulServer s, AppProperties p) {
	   theServer = s;
	   theProperties = p;
   }

   @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
   public void serverIncomingRequestPreProcessed(RequestDetails reqdetails, ResponseDetails resdetails, HttpServletRequest request, HttpServletResponse response, IBaseResource resource ) {
	   logger.info("serverIncomingRequestPreProcessed:");

	   try {
		   BufferedReader reader = request.getReader();
		   Stream<String> lines = reader.lines();
		   theRequestBody = lines.collect(Collectors.joining("\r\n"));
	   }
	   catch(IOException e) {
		   theRequestBody = null;
	   }
   }

   @Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
   public void serverIncomingRequestPostProcessed(RequestDetails reqdetails, ResponseDetails resdetails, HttpServletRequest request, HttpServletResponse response, IBaseResource resource ) {
	   logger.info("serverIncomingRequestPostProcessed:");

	   theReqDetails = reqdetails;
	   theResDetails = resdetails;
	   theRequest = request;
	   theResponse = response;
	   theResource = null;
   }

   @Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
   public void serverProcessingCompleted(HookParams theParam) {
	   logger.info("serverProcessingCompleted:");

	   if (theProperties != null && theProperties.getDump_enabled())
		   dumpResponseToFile();
   }

   private void dumpResponseToFile() {
//	   RequestDetails reqdetails = theReqDetails;
//	   ResponseDetails resdetails = theResDetails;
	   HttpServletRequest request = theRequest;
	   HttpServletResponse response = theResponse;

	   if (request == null)
		   return;

	   StringBuilder sb = new StringBuilder("");

	   if (request != null) {
		   sb.append("REMOTE HOST:" + request.getRemoteAddr() + ":" + request.getRemotePort() + "\n");
		   sb.append("LOCAL HOST:" + request.getLocalAddr() + ":" + request.getLocalPort() + "\n");

		   String req = request.getMethod() + " " + request.getRequestURI();
		   if (request.getQueryString() != null && !request.getQueryString().isEmpty())
			   req += "?" + request.getQueryString();
		   sb.append("REQUEST:" + req + "\n");
		   try {
				sb.append("REQUEST(D):" + URLDecoder.decode(req, "UTF-8") + "\n");
			} catch (UnsupportedEncodingException e) {
				sb.append("REQUEST(D):(error)" + e.getMessage() + "\n");
			}
		   sb.append("REQUEST HEADER: \n");
		   Enumeration <String> headers = request.getHeaderNames();
		   for (String header=headers.nextElement(); header != null; header = headers.nextElement()) {
			   sb.append("    " + header + "=" + request.getHeader(header) + "\n");
		   }

		   if (theRequestBody != null) {
			   sb.append("REQUEST BODY: \n");
			   sb.append(theRequestBody);
			   sb.append("\n");
		   }
	   }

	   if (response != null) {
		   sb.append("STATUS: " + response.getStatus() + "\n");

		   sb.append("RESPONSE HEADER: \n");
		   Collection <String> headers = response.getHeaderNames();
		   for (String header : headers) {
			   sb.append("    " + header + "=" + response.getHeader(header) + "\n");
		   }

		   sb.append("RESPONSE BODY:\n");
		   IBaseResource resource = theResource;
		   if (resource != null) {
			   sb.append(theServer.getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(resource));
			   sb.append("\n");
		   }
	   }

	   logger.info(sb.toString());

	   String dir = theProperties.getDump_directory();
	   if (dir == null)
		   dir = ".";
	   String path = String.format("%s/%s-%s.log",
			   dir,
			   new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()),
			   request.getRemoteAddr());
	   WriteTextFile(sb.toString(), path, "UTF-8");

//	   sb.append("  METHOD:" + request.getMethod() + "\n");
//	   sb.append("   CPATH:" + request.getContextPath() + "\n");
//	   sb.append("   TPATH:" + request.getPathTranslated() + "\n");
//	   sb.append("   SPATH:" + request.getServletPath() + "\n");
//	   sb.append("     URI:" + request.getRequestURI() + "\n");
//	   sb.append("   QUERY:" + request.getQueryString() + "\n");
//
//	   sb.append("  PARAMS: \n");
//	   Enumeration <String> params = request.getParameterNames();
//	   for (String param = params.nextElement(); param != null; param = params.nextElement()) {
//		   sb.append("    " + param + "=" + request.getParameter(param) + "\n");
//	   }


   }

	/**
	 * 文字列をファイルに出力する
	 *
	 * @param text			出力対象の文字列
	 * @param fname			出力先のファイルのフルパス名
	 * @param encoding		文字コード
	 * @return				成功したらtrueを返す
	 */
	public static boolean WriteTextFile(String text, String fname, String encoding) {
		PrintWriter pw = null;

		fname = fname.replaceAll(":", "_");
		try{
			FileOutputStream fos = new FileOutputStream(new File(fname));
			pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(fos, encoding)));

			pw.println(text);
		}catch(IOException e){
			logger.error("Failed to write text file:" + fname);
			logger.error(e.toString());
			  return false;
		}
		finally {
			if (pw != null)
				pw.close();
		}

		return true;
	}

   @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
   public void serverOutgoingResponse(RequestDetails reqdetails, ResponseDetails resdetails, HttpServletRequest request, HttpServletResponse response, IBaseResource resource ) {
	   logger.info("serverOutgoingResponse:");

	   theResource = resource;
   }

   @Hook(Pointcut.SERVER_PROCESSING_COMPLETED_NORMALLY)
   public void serverProcessingCompletedNormally(RequestDetails reqdetails, ServletRequestDetails srdetails) {
	   logger.info("serverProcessingCompletedNormally:");
   }
}