package com.flickr.oauth;

import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.FlickrApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

public class OAuthHelper {
  final private String apiKey, apiSecret;

  public String getApiKey() {
    return apiKey;
  }

  private OAuthService service;

  public OAuthHelper(String apiKey, String apiSecret) {
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    service = new ServiceBuilder().provider(FlickrApi.class).apiKey(apiKey)
        .apiSecret(this.apiSecret).build();
  }

  public String getAuthUrl(Token requestToken) {
    String authUrl = service.getAuthorizationUrl(requestToken);
    return authUrl;
  }

  public Token getAccessToken(String verifier, Token requestToken) {

    Verifier v = new Verifier(verifier);
    Token accessToken = service.getAccessToken(requestToken, v);
    return accessToken;
  }

  public Token getRequestToken() {
    return service.getRequestToken();
  }

  public void signRequest(OAuthRequest request, Token accessToken) {
    service.signRequest(accessToken, request);
  }
  public Response sendRequest(OAuthRequest request, Token accessToken) {
    signRequest(request, accessToken);
    Response response = request.send();
    return response;
  }
}
