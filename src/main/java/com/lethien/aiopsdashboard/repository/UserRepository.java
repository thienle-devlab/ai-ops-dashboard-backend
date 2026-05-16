package com.lethien.aiopsdashboard.repository;

import com.lethien.aiopsdashboard.entity.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    public Optional<User> findByEmail(String email) {
        // Trả về empty — đủ để app khởi động, login sẽ báo 401 (bình thường)
        return Optional.empty();
    }
}
