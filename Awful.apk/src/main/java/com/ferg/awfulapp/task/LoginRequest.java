package com.ferg.awfulapp.task;

import android.content.Context;
import android.net.Uri;

import com.ferg.awfulapp.constants.Constants;
import com.ferg.awfulapp.network.NetworkUtils;
import com.ferg.awfulapp.preferences.AwfulPreferences;
import com.ferg.awfulapp.preferences.Keys;
import com.ferg.awfulapp.util.AwfulError;

import org.jsoup.nodes.Document;

/**
 * Created by matt on 8/8/13.
 */
public class LoginRequest extends AwfulRequest<Boolean> {
    private String username;
    public LoginRequest(Context context, String username, String password) {
        super(context, null);
        this.username = username;
        addPostParam(Constants.PARAM_ACTION, "login");
        addPostParam(Constants.PARAM_USERNAME, username);
        addPostParam(Constants.PARAM_PASSWORD, password);
    }

    @Override
    protected String generateUrl(Uri.Builder urlBuilder) {
        return Constants.FUNCTION_LOGIN_SSL;
    }

    @Override
    protected Boolean handleResponse(Document doc) throws AwfulError {
        Boolean result = NetworkUtils.saveLoginCookies(getContext());
        if(result){
            AwfulPreferences prefs = AwfulPreferences.getInstance(getContext());
            prefs.setPreference(Keys.USERNAME, username);
        }
        return result;
    }

    @Override
    protected boolean handleError(AwfulError error, Document doc) {
        if(error.networkResponse != null && error.networkResponse.statusCode == 302){
            Boolean result = NetworkUtils.saveLoginCookies(getContext());
            if(result){
                AwfulPreferences prefs = AwfulPreferences.getInstance(getContext());
                prefs.setPreference(Keys.USERNAME, username);
            }
            return result;
        }else{
            return error.isCritical();
        }
    }
}
