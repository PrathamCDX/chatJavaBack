package com.example.demo.repository;

import com.example.demo.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    @Query("select ur.role from UserRole ur where ur.user.id = :userId")
    List<Role> findAllByUserId(@Param("userId") Long userId);

    @Query("select ur.role.name from UserRole ur where ur.user.id = :userId")
    List<String> findNamesByUserId(@Param("userId") Long userId);
}
