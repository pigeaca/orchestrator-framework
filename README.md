# ⚙️ WorkflowEngine — Kotlin DSL Orchestration Framework

| ![img](docs/readmi-picture.png) | ![img](docs/readmi-img2.png) |
|-------------------|------------------------------|


> Coroutine-based workflow engine with a powerful type-safe DSL, rollback support, execution graph planning, and gRPC integration.

---

## 🚀 Overview

**WorkflowEngine** is a Kotlin-based orchestration framework for modeling and executing distributed business processes.  
It compiles declarative DSLs into an execution graph and runs workflows step-by-step via message queues, with full rollback support and horizontal scalability.

Designed for complex, event-driven microservice architectures.

---

## ✨ Features

✅ Type-safe Kotlin DSL to define workflows  
✅ Full rollback chain with correct reverse dependencies  
✅ Step-by-step execution graph compilation  
✅ Suspend-friendly coroutine runtime  
✅ Queue-based execution model  
✅ gRPC interfaces for polling, result submission, and orchestration lifecycle  
✅ Pluggable architecture (queue, store, executor)

---

## 📦 Architecture

```text
      ┌─────────────────────────────┐
      │   Your Workflow DSL         │
      └────────────┬────────────────┘
                   │
                   ▼
        ┌────────────────────────────┐
        │ Execution Plan Compiler    │
        └────────────┬───────────────┘
                     ▼
       ┌─────────────────────────────┐
       │      Workflow Engine        │
       └────────────┬────────────────┘
       ┌────────────┴─────────────┐
       ▼                          ▼
 gRPC API               Queue Dispatcher
                            │
                            ▼
                External Services (Workers)
```

## Example DSL

```kotlin
orchestration(
    name = "UserRegistration",
    workflowRequest = CreateUserRequest::class,
    workflowResponse = CreateUserResponse::class
) { definitionContext ->

    // === Step 1: Create Account ===
    val createAccountStep = step(
        type = "createAccount", queue = "account-queue",
        call = activity(UserService::class).callSuspend(UserService::createUser) {
            val request = useRequest(definitionContext)
            CreateUserInput(email = request.username, phone = request.displayName)
        },
        rollbackStep = { currentStep ->
            rollbackStep(
                type = "deleteAccount", queue = "account-queue",
                call = activity(UserService::class).callSuspend(UserService::removeUser) {
                    val result = resultOf(currentStep)
                    result?.userId
                }
            )
        }
    )

    // === Step 2: Create Key ===
    step(
        type = "createAccountKey", queue = "key-queue",
        call = activity(UserKeyService::class).callSuspend(UserKeyService::createUserKey) {
            val request = useRequest(definitionContext)
            val user = resultOf(createAccountStep) ?: return@call null
            CreateKey(accountId = user.userId, key = request.key)
        },
        rollbackStep = { currentStep ->
            rollbackStep(
                type = "deleteKey", queue = "key-queue",
                call = activity(UserKeyService::class).callSuspend(UserKeyService::removeUserKey) {
                    val keyResult = resultOf(currentStep)
                    keyResult?.keyId
                }
            )
        }
    )

    // === Completion Handlers ===
    onComplete {
        val result = resultOf(createAccountStep)
        CreateUserResponse(userId = result?.userId, error = null)
    }

    onFailure {
        val request = useRequest(definitionContext)
        CreateUserResponse(userId = null, error = "Failed to create user ${request.username}")
    }
}
```

## 🧩 Declaring Activity Services

```kotlin
@ActivityService
interface UserService {

    @Activity
    fun createUser(input: CreateUserInput?): CreateUserResult

    @Activity
    fun removeUser(accountId: String?): CreateUserResult
}

@ActivityService
interface UserKeyService {

    @Activity
    fun createUserKey(input: CreateKey?): CreateKeyResponse

    @Activity
    fun removeUserKey(keyId: String?): CreateKeyResponse
}
```

### ✅ Auto-registration

The **Spring Boot integration** will:

- 🔍 Discover all `@ActivityService` interfaces
- 🔧 Automatically wire and register their implementations
- 🧠 Allow the engine to invoke `@Activity` methods by service and method name
- ❌ Require **no manual wiring or configuration**

> Just annotate your service interfaces — the engine handles the rest.

## 🔧 Tech Stack

- 🟨 **Kotlin** + **Coroutines** — modern, suspend-friendly execution engine
- 🔌 **gRPC / Protobuf** — lightweight and strongly typed inter-service communication
- 📦 **Jackson** — for JSON-based input/output serialization

---

## 🛠️ How It Works

1. ✍️ **Define workflows** with the expressive Kotlin DSL
2. 🧠 **Compile** them into a dependency-aware execution plan
3. 🚀 **Dispatch** steps via queue to external workers
4. 🎯 **Workers poll, execute**, and submit results back to the engine
5. 🔁 **Rollback** automatically triggered on failure if defined
6. 📊 **Monitor or visualize** workflow state *(UI planned)*

---

## 🎯 Use Cases

- ✅ Multi-step **user onboarding**
- ✅ Distributed **transactional sagas**
- ✅ Async **microservice orchestration**
- ✅ Low-code **automation pipelines**
- ✅ **Rollbackable** long-running processes

---

## 🧪 Roadmap

- [x] Workflow DSL + rollback chaining
- [x] gRPC-based task polling and result submission
- [x] Execution graph compiler
- [ ] Persistent workflow state store
- [ ] Visual dashboard for orchestration status
- [ ] Retry logic & deduplication support
- [ ] Cross-region worker distribution

---

## 📜 License

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).  
You may freely use, modify, and distribute this project under the terms of the license. 

Build better orchestrated systems ⚙️