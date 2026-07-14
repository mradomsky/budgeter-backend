package com.radomskyi.budgeter.domain.service;

import com.radomskyi.budgeter.dto.AccountResponse;
import java.util.List;

public interface AccountServiceInterface {

    List<AccountResponse> getAllAccounts();
}
