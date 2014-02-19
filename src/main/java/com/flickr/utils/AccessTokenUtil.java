package com.flickr.utils;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;

import org.scribe.model.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flickr.oauth.OAuthHelper;

public class AccessTokenUtil {
  private static final Logger LOG = LoggerFactory
      .getLogger(AccessTokenUtil.class);
  private static File accessTokenFile = new File(
      System.getProperty("java.io.tmpdir"),
      "flickr.tok");;

  public static Token readAccessToken() throws IOException {
    if (!accessTokenFile.exists())
      return null;
    BufferedReader reader = null;
    Token token = null;
    try {
      reader = new BufferedReader(new FileReader(accessTokenFile));
      token = new Token(reader.readLine(), reader.readLine());
    } catch (Exception e) {
      LOG.error("Error while reading access token file", e);
    } finally {
      if (reader != null)
        reader.close();
    }
    return token;
  }

  public static Token getAccessToken(OAuthHelper oAuth)
      throws IOException {
    Token requestToken = oAuth.getRequestToken();
    String authUrl = oAuth.getAuthUrl(requestToken);
    authUrl += "&perms=delete";
    System.out.println("Enter the verifier code in console");
    if (!Desktop.isDesktopSupported()) {
      System.out.println("Please open the URL in browser" + authUrl);
    } else {
      Desktop desktop = Desktop.getDesktop();
      if (!desktop.isSupported(Desktop.Action.BROWSE)) {
        System.out.println("Please open the URL in browser :" + authUrl);
      } else {
        try {
          desktop.browse(new URI(authUrl));
        } catch (URISyntaxException e) {
          System.out.println("Please open the URL in browser :" + authUrl);
        }
      }
    }
    String verifier = new BufferedReader(new InputStreamReader(System.in))
        .readLine();

    return oAuth.getAccessToken(verifier, requestToken);
  }
  public static void writeAccessToken(Token token) throws IOException {
    if (!accessTokenFile.exists())
      accessTokenFile.createNewFile();
    BufferedWriter writer = new BufferedWriter(new FileWriter(accessTokenFile));
    writer.write(token.getToken());
    writer.newLine();
    writer.write(token.getSecret());
    writer.close();

  }
}
