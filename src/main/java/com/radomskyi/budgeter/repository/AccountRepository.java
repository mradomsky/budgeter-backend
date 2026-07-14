package com.radomskyi.budgeter.repository;

import com.radomskyi.budgeter.domain.entity.budgeting.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByExternalId(String externalId);
}
