package com.automation.domain.keyboard;

import android.view.inputmethod.InputConnection;

public interface InputConnectionProvider {
    InputConnection current();
}
