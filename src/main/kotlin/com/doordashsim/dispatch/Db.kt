package com.doordashsim.dispatch

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

object Db {
    fun dataSource(jdbcUrl: String, user: String, password: String): HikariDataSource {
        val config = HikariConfig()
        config.jdbcUrl = jdbcUrl
        config.username = user
        config.password = password
        config.maximumPoolSize = 8
        config.minimumIdle = 1
        return HikariDataSource(config)
    }
}
