package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.model.PlaceEvent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Debug and staging only: arrives at or leaves a place as if its geofence fired, through the same rules. A simulated
// departure skips the five-minute stay.
interface ArrivalSimulator {
    fun simulate(placeId: UUID, event: PlaceEvent, after: Duration)
}

// Runs on the application scope, so a delayed arrival still comes after leaving the app or locking the screen.
@Singleton
class DefaultArrivalSimulator @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val arrivalHandler: ArrivalHandler,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ArrivalSimulator {
    override fun simulate(placeId: UUID, event: PlaceEvent, after: Duration) {
        scope.launch {
            delay(after.toMillis())
            placeRepository.markNotified(placeId, event, null)
            arrivalHandler.remind(listOf(placeId), event, near = null)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ArrivalSimulatorModule {
    @Binds
    abstract fun arrivalSimulator(simulator: DefaultArrivalSimulator): ArrivalSimulator
}
