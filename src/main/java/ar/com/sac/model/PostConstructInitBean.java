package ar.com.sac.model;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import javax.annotation.PostConstruct;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:application.properties")
public class PostConstructInitBean {
   
   @Value("${http.proxyHost}")
   private String proxyHost;
   
   @Value("${http.proxyPort}")
   private String proxyPort;
   
   @Value("${https.proxyHost}")
   private String httpsProxyHost;
   
   @Value("${https.proxyPort}")
   private String httpsProxyPort;
   
   @Value("${http.proxyUser}")
   private String proxyUser;
   
   @Value("${http.proxyPassword}")
   private String proxyPassword;
   
   @PostConstruct
   public void init() {
      System.out.println( "---------- BEGIN of Stock Alerts INITIALIZATION -----------" );
      initializeProxyAuthenticator();
      testConnection();
      System.out.println( "---------- END of Stock Alerts INITIALIZATION -----------" );
   }
   
   private void initializeProxyAuthenticator() {
      System.setProperty("http.proxyHost", proxyHost);
      System.setProperty("http.proxyPort", proxyPort);
      System.setProperty("https.proxyHost", httpsProxyHost);
      System.setProperty("https.proxyPort", httpsProxyPort);

      if (!StringUtils.isEmpty( proxyUser )) {
         System.out.println( "Using Proxy User: " + proxyUser );
          Authenticator.setDefault(
            new Authenticator() {
              @Override
              public PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                  proxyUser, proxyPassword.toCharArray()
                );
              }
            }
          );
      }
  }
   
   private void testConnection() {

      URL url;
      try {
         url = new URL("https://finance.yahoo.com/");
         
         BufferedReader in = new BufferedReader(
         new InputStreamReader(url.openStream()));
   
         String inputLine;
         while ((inputLine = in.readLine()) != null)
             System.out.println(inputLine);
         in.close();
         
      } catch (Exception e) {
         e.printStackTrace();
      }
  }
   
   
}



 
   