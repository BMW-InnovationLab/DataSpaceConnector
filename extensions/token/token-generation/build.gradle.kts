/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - initial API and implementation
 *
 */

plugins {
    `java-library`
}

val rsApi: String by project
val mockitoVersion: String by project
val nimbusVersion: String by project

dependencies {
    api(project(":spi:core-spi"))
    implementation("com.nimbusds:nimbus-jose-jwt:${nimbusVersion}")

    api("jakarta.ws.rs:jakarta.ws.rs-api:${rsApi}")
    testImplementation("org.mockito:mockito-core:${mockitoVersion}")
}


publishing {
    publications {
        create<MavenPublication>("token-generation") {
            artifactId = "token-generation"
            from(components["java"])
        }
    }
}
