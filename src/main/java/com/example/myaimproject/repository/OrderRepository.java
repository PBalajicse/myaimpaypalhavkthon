package com.example.myaimproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.myaimproject.entity.OrderEntity;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>{
	

}
