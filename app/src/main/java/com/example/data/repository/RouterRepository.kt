package com.example.data.repository

import com.example.data.local.database.RouterProfileDao
import com.example.data.local.entity.RouterProfileEntity
import com.example.data.mapper.RouterMapper.toDomainList
import com.example.domain.model.RouterProfile
import com.example.domain.repository.IRouterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RouterRepository(private val routerProfileDao: RouterProfileDao) : IRouterRepository {
    override val allRouters: Flow<List<RouterProfileEntity>> = routerProfileDao.getAllRouters()
    override val routerProfiles: Flow<List<RouterProfile>> = allRouters.map { it.toDomainList() }

    override suspend fun insert(router: RouterProfileEntity): Long {
        return routerProfileDao.insert(router)
    }

    override suspend fun update(router: RouterProfileEntity) {
        routerProfileDao.update(router)
    }

    override suspend fun delete(router: RouterProfileEntity) {
        routerProfileDao.delete(router)
    }

    override suspend fun getById(id: Long): RouterProfileEntity? {
        return routerProfileDao.getById(id)
    }

    override suspend fun getDefault(): RouterProfileEntity? {
        return routerProfileDao.getDefault()
    }

    override suspend fun setDefault(id: Long) {
        routerProfileDao.setDefault(id)
    }
}
