package dev.kord.core.builder.kord

import dev.kord.cache.api.DataCache
import dev.kord.core.ClientResources
import dev.kord.core.Kord
import dev.kord.core.gateway.DefaultMasterGateway
import dev.kord.core.gateway.handler.GatewayEventInterceptor
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.gateway.Gateway
import dev.kord.gateway.builder.Shards
import dev.kord.rest.service.RestClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow

public abstract class RestOnlyBuilder : AbstractKordBuilder(), HasApplication {

    public fun build(): Kord {
        val client = httpClient.configure()
        val selfId = actualApplicationId

        val resources = ClientResources(
            token,
            selfId,
            Shards(0),
            maxConcurrency = 1,
            client,
            EntitySupplyStrategy.rest,
        )

        val rest = RestClient(buildRequestHandler(resources))

        return Kord(
            resources = resources,
            cache = @OptIn(ExperimentalCoroutinesApi::class) DataCache.none(),
            gateway = DefaultMasterGateway(mapOf(0 to Gateway.none())),
            rest = rest,
            selfId = selfId,
            eventFlow = MutableSharedFlow(),
            dispatcher = defaultDispatcher,
            interceptor = GatewayEventInterceptor.none(),
        )
    }
}
