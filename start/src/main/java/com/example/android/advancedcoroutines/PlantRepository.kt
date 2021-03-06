/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.advancedcoroutines

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import com.example.android.advancedcoroutines.util.CacheOnSuccess
import com.example.android.advancedcoroutines.utils.ComparablePair
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

/**
 * Repository module for handling data operations.
 *
 * This PlantRepository exposes two UI-observable database queries [plants] and
 * [getPlantsWithGrowZone].
 *
 * To update the plants cache, call [tryUpdateRecentPlantsForGrowZoneCache] or
 * [tryUpdateRecentPlantsCache].
 */
class PlantRepository private constructor(
    private val plantDao: PlantDao,
    private val plantService: NetworkService,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * Fetch a list of [Plant]s from the database.
     * Returns a LiveData-wrapped List of Plants.
     */
    val plants : LiveData<List<Plant>> = liveData {
        val plantsLiveData = plantDao.getPlants()
        Timber.d("plantsLiveData: $plantsLiveData")

        val customSortOrder = plantsListOrderCache.getOrAwait()
        Timber.d("customSortOrder: $customSortOrder")

        emitSource(plantsLiveData.map {
            plantList -> plantList.applySort(customSortOrder)
        })
    }

    /**
     * Fetch a list of [Plant]s from the database that matches a given [GrowZone].
     * Returns a LiveData-wrapped List of Plants.
     */
    fun getPlantsWithGrowZone(growZone: GrowZone) = liveData<List<Plant>> {
        val plantsGrowZoneLiveData = plantDao.getPlantsWithGrowZoneNumber(growZone.number)
        Timber.d("plantsGrowZoneLiveData: $plantsGrowZoneLiveData")
        val customSortOrder = plantsListOrderCache.getOrAwait()
        Timber.d("customSortOrder: $customSortOrder")

        emitSource(plantsGrowZoneLiveData.map {
            plantList -> plantList.applySort(customSortOrder)
        })
    }

    /**
     * Returns true if we should make a network request.
     */
    private fun shouldUpdatePlantsCache(): Boolean {
        // suspending function, so you can e.g. check the status of the database here
        return true
    }

    /**
     * Update the plants cache.
     *
     * This function may decide to avoid making a network requests on every call based on a
     * cache-invalidation policy.
     */
    suspend fun tryUpdateRecentPlantsCache() {
        Timber.d("shouldUpdatePlantsCache: ${shouldUpdatePlantsCache()}")
        if (shouldUpdatePlantsCache()) {
            Timber.d("fetchRecentPlants: ${fetchRecentPlants()}")
            fetchRecentPlants()
        }
    }

    /**
     * Update the plants cache for a specific grow zone.
     *
     * This function may decide to avoid making a network requests on every call based on a
     * cache-invalidation policy.
     */
    suspend fun tryUpdateRecentPlantsForGrowZoneCache(growZoneNumber: GrowZone) {
        Timber.d("shouldUpdatePlantsCache: ${shouldUpdatePlantsCache()}")
        if (shouldUpdatePlantsCache()) {
            Timber.d("fetchPlantsForGrowZone ${fetchPlantsForGrowZone(growZoneNumber)}")
            fetchPlantsForGrowZone(growZoneNumber)
        }
    }

    /**
     * Fetch a new list of plants from the network, and append them to [plantDao]
     */
    private suspend fun fetchRecentPlants() {
        val plants = plantService.allPlants()
        plantDao.insertAll(plants)
    }

    /**
     * Fetch a list of plants for a grow zone from the network, and append them to [plantDao]
     */
    private suspend fun fetchPlantsForGrowZone(growZone: GrowZone) {
        val plants = plantService.plantsByGrowZone(growZone)
        Timber.d("Plants: $plants")
        plantDao.insertAll(plants)
        Timber.d("Inserted plants")
    }

    private var plantsListOrderCache = CacheOnSuccess(onErrorFallback = { listOf<String>() }){
        plantService.customPlantSortOrder()
    }

    private fun List<Plant>.applySort(customSortOrder: List<String>): List<Plant>{
        return sortedBy { plant ->
            val positionForItem = customSortOrder.indexOf(plant.plantId).let { order ->
                if(order > -1) order
                else Int.MAX_VALUE
            }
            Timber.d("${positionForItem}")
            Timber.d("${ComparablePair(positionForItem, plant.name)}")

            ComparablePair(positionForItem, plant.name)

        }
    }

    companion object {
        // For Singleton instantiation
        @Volatile
        private var instance: PlantRepository? = null

        fun getInstance(plantDao: PlantDao, plantService: NetworkService) =
            instance ?: synchronized(this) {
                instance ?: PlantRepository(plantDao, plantService).also { instance = it }
            }
    }
}
