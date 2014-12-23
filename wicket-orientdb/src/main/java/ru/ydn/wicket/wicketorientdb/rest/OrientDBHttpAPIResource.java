package ru.ydn.wicket.wicketorientdb.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.http.WebRequest;
import org.apache.wicket.request.http.WebResponse;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.AbstractResource;
import org.apache.wicket.request.resource.SharedResourceReference;
import org.apache.wicket.util.io.IOUtils;
import org.apache.wicket.util.lang.Args;
import org.apache.wicket.util.string.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.ydn.wicket.wicketorientdb.IOrientDbSettings;
import ru.ydn.wicket.wicketorientdb.OrientDbWebApplication;
import ru.ydn.wicket.wicketorientdb.OrientDbWebSession;


public class OrientDBHttpAPIResource extends AbstractResource
{
	public static final String MOUNT_PATH = "/orientdb";
	public static final String ORIENT_DB_KEY=OrientDBHttpAPIResource.class.getSimpleName();
	
	private static final Logger LOG = LoggerFactory.getLogger(OrientDBHttpAPIResource.class);
	
	private String orientDbHttpURL;
	
	public OrientDBHttpAPIResource()
	{
		this("http://localhost:2480/");
	}
	
	public OrientDBHttpAPIResource(String orientDbHttpURL)
	{
		Args.notNull(orientDbHttpURL, "orientDbHttpURL");
		if(!orientDbHttpURL.endsWith("/")) orientDbHttpURL+="/";
		this.orientDbHttpURL = orientDbHttpURL;
	}
	
	@Override
	protected ResourceResponse newResourceResponse(Attributes attributes) {
		final WebRequest request = (WebRequest) attributes.getRequest();
		final HttpServletRequest httpRequest = (HttpServletRequest) request.getContainerRequest();
		final PageParameters params = attributes.getParameters();
		final ResourceResponse response = new ResourceResponse();
		if(response.dataNeedsToBeWritten(attributes))
		{
			StringBuilder sb = new StringBuilder(orientDbHttpURL);
			for(int i=0; i<params.getIndexedCount();i++)
			{
				if(i==1)
				{
					//replace provided database name
					sb.append(OrientDbWebSession.get().getDatabase().getName()).append('/');
				}
				else
				{
					sb.append(params.get(i).toString()).append('/');
				}
			}
			if(sb.charAt(sb.length()-1)=='/')sb.setLength(sb.length()-1);
			String queryString = request.getUrl().getQueryString();
			if(!Strings.isEmpty(queryString)) sb.append('?').append(queryString);
			
			final String url = sb.toString();
			final StringWriter sw = new StringWriter();
			final PrintWriter out = new PrintWriter(sw);
			HttpURLConnection con=null;
			try
			{
				URL orientURL = new URL(url);
				con = (HttpURLConnection)orientURL.openConnection();
				con.setDoInput(true);
				/*Enumeration<String> headers = httpRequest.getHeaderNames();
						while(headers.hasMoreElements())
						{
							String header = headers.nextElement();
							Enumeration<String> headerValues = httpRequest.getHeaders(header);
							while(headerValues.hasMoreElements())
							{
								String value = headerValues.nextElement();
								con.addRequestProperty(header, value);
							}
						}*/
				con.setUseCaches(false);
				
				String method = httpRequest.getMethod();
				con.setRequestMethod(method);
				if("post".equalsIgnoreCase(method) || "put".equalsIgnoreCase(method))
				{
					con.setDoOutput(true);
					IOUtils.copy(httpRequest.getInputStream(), con.getOutputStream());
				}
				IOUtils.copy(con.getInputStream(), out, "UTF-8");
				response.setStatusCode(con.getResponseCode());
				response.setContentType(con.getContentType());
			} catch (IOException e)
			{
				LOG.error("Can't communicate with OrientDB REST", e);
				if(con!=null)
				{
					try
					{
						response.setError(con.getResponseCode(), con.getResponseMessage());
						InputStream errorStream = con.getErrorStream();
						if(errorStream!=null) IOUtils.copy(errorStream, out, "UTF-8");
					} catch (IOException e1)
					{
						LOG.error("Can't response by error", e1);
					}
				}
			}
			response.setWriteCallback(new WriteCallback() {
				
				@Override
				public void writeData(Attributes attributes) throws IOException {
					attributes.getResponse().write(sw.toString());
				}
			});
		}
		return response;
	}
	
	public void register(WebApplication app)
	{
		app.getSharedResources().add(ORIENT_DB_KEY, this);
		app.mountResource(MOUNT_PATH, new SharedResourceReference(ORIENT_DB_KEY));
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				String username;
				String password;
				OrientDbWebSession session = OrientDbWebSession.get();
				if(session.isSignedIn())
				{
					username = session.getUsername();
					password = session.getPassword();
				}
				else
				{
					IOrientDbSettings settings = OrientDbWebApplication.get().getOrientDbSettings();
					username = settings.getDBUserName();
					password = settings.getDBUserPassword();
				}
				return new PasswordAuthentication (username, password.toCharArray());
			}
			
		});
		CookieHandler.setDefault(new PersonalCookieManager());
	}

}
