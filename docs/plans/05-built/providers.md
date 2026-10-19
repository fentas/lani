# Plan 5, built: more than one LLM provider

*Built 2026-10-18 (the overnight round, design point 6: "Anthropic as now, and OpenAI (API key, and 'Sign in with
ChatGPT') next to it"). Branch `providers`. Code: `companion/android/app/src/main/java/si/lanisce/lani/tutor/`
(`Provider.kt`, `Loop.kt`, `OpenAI.kt`, `ChatGPT.kt`), the panel `ui/tutor/ShadowPanel.kt`, `companion/tutor/prices.json`,
`companion/bin/shadow-compare`.*

*Extended 2026-10-19 (branch `providers2`): Gemini direct with a Google AI Studio key, and OpenRouter ("one sign-in,
every model"), for the live tutor, the studio and the matrix ([Gemini](#gemini-the-gemini-api-direct),
[OpenRouter](#openrouter-one-sign-in-every-model)). Code: `tutor/Gemini.kt`, `tutor/OpenRouter.kt`, `tutor/Loopback.kt`
(the loopback ChatGPT's sign-in and OpenRouter's share), the Tutor section `ui/tutor/TutorSettings.kt`.*

## What

- **A provider layer.** The tool loop runs the same on any LLM provider. A provider builds the request, streams the
  call and returns one normalized step: the text, the tool calls (ids and inputs), the usage (cached and reasoning
  tokens too), the stop reason. Errors come in the same classes as before: auth, rate limit, overload, the credit or
  the allowance out, a refusal.
- **Anthropic works exactly as before.** `AnthropicWireTest` pins a request byte for byte and passed on the code
  before the layer (commit 079b515) and after it. It covers the prefix with its 1-hour breakpoint, adaptive thinking,
  the effort and the effort messages with their beta, the thinking blocks and the tool results.
- **OpenAI next to it.** The Responses API, by API key or by "Sign in with ChatGPT", where the learner's ChatGPT plan
  pays.
- **The matrix gets OpenAI lanes:** GPT-6 Luna, GPT-6.1 Sol and GPT-6 Astra, at the efforts each one takes. The lanes
  follow the same rules as Anthropic's: their own thread, the notebook read-only, writes as dry runs. Their ids carry
  the provider (`openai:gpt-6.1-sol@medium`). `shadow-compare` reads them and shows the plan's API-equivalent price.
- **The primary shadow stays Anthropic Balanced.** No preset changed. "Also run on" can also pick a GPT-6 model.
- **Gemini and OpenRouter** (2026-10-19) on the same interfaces: the Gemini API's `streamGenerateContent` with a
  Google AI Studio key, OpenRouter's chat completions with the learner's own key from its PKCE sign-in. The live
  tutor and the studio can run on either; the matrix gets their lanes (`gemini:gemini-4-argon@high`,
  `openrouter:google/gemini-4-argon@medium`).

## The provider API (for the live tutor)

Everything is in `tutor/Provider.kt`. The live tutor builds on these types:

```kotlin
enum class ProviderId { ANTHROPIC, OPENAI, GEMINI, OPENROUTER; companion object { fun of(model: String): ProviderId } }
    // OpenRouter's ids have a "/" (google/gemini-4-argon), Gemini's begin with gemini-, OpenAI's gpt-, o3 …, else Claude
object Billing { API_KEY = "api_key"; CHATGPT_PLAN = "chatgpt_plan"; FREE_TIER = "free_tier"; OPENROUTER = "openrouter_credits" }
object Stop { END_TURN, TOOL_USE, MAX_TOKENS, REFUSAL }            // the loop's stop reasons (strings)

data class Step(                                                    // one call's response, normalized
    val items: List<JsonObject>,        // what the thread keeps, in the provider's own format
    val toolCalls: List<ToolUse>,       // ToolUse(id, name, input, badJson)
    val text: String, val stopReason: String?, val stopDetails: JsonObject?,
    val usage: Usage,                   // input, cacheWrite5m/1h, cacheRead, output, reasoning
    val model: String?, val id: String?, val firstTokenMs: Long?, val totalMs: Long, val visibleChars: Int,
    val kept: List<JsonObject>,         // what stays of a response cut off at max tokens
    val inputTransformations: String?, val rawUsage: RawUsage,
    val cost: Double?,                  // the provider's own price of the call (OpenRouter's usage.cost); null: the table's
)

interface TutorProvider {
    val id: ProviderId
    val format: ThreadFormat
    val billing: String
    suspend fun models(): List<ModelInfo>
    fun prefixHash(prefix: Prefix): String
    fun request(prefix: Prefix, model: String, task: Task, messages: List<JsonObject>, maxTokens: Int, effort: String?): JsonObject
    suspend fun call(body: JsonObject, onDelta: () -> Unit = {}): Step   // throws ApiError
}

interface ThreadFormat {                                            // AnthropicFormat, OpenAIFormat, GeminiFormat, ChatFormat
    fun user(blocks: List<JsonObject>): JsonObject
    fun append(messages: List<JsonObject>, blocks: List<JsonObject>, top: String?, want: String?): List<JsonObject>
    fun pending(messages: List<JsonObject>): List<ToolUse>?         // null: the next step is a call
    fun results(calls: List<ToolUse>, outcomes: List<ToolOutcome>): List<JsonObject>
    companion object { fun of(model: String): ThreadFormat }
}

object Threads { fun answered(messages): Boolean; fun text(items): String }

class TutorLoop(provider: TutorProvider, runner: ToolRunner, maxCalls = 8, budgetOk, sleep, maxRetries = 3, maxWaitMs = 30_000)
    // also TutorLoop(transport: TutorTransport, …): the Messages API, as before
    suspend fun run(prefix, model, task, messages: MutableList<JsonObject>, callsDone, save, onCall, onTool, effort, maxTokens): Result

class AnthropicProvider(transport: TutorTransport)                  // AnthropicTransport, unchanged
class OpenAIProvider(transport: OpenAITransport, billing: String, renew: (suspend () -> Boolean)? = null)
class OpenAITransport(bearer: suspend () -> OpenAIBearer?, client: OkHttpClient = defaultClient, clock)
class OpenAICredentials(apiKey: KeyVault, chatgpt: ChatGPTAuth) { suspend fun available(): Set<String>; suspend fun bearer(billing): OpenAIBearer? }
class ChatGPTAuth(vault: KeyVault, http, clock, random, appName = "Lani") {
    suspend fun begin(port: Int = 0): SignIn                        // SignIn.url to open; SignIn.await(): ChatGPTSession
    suspend fun accessToken(force: Boolean = false): String?        // refreshed near expiry, rotation kept, serialized
    suspend fun signOut(): String?                                  // revoked; null when confirmed
    suspend fun session(): ChatGPTSession?
}

class GeminiProvider(transport: GeminiTransport, billing: String = Billing.API_KEY)    // or Billing.FREE_TIER
class GeminiTransport(key: suspend () -> String?, client, clock, pacer: GeminiPacer?, onFreeTier: () -> Unit)
class GeminiCredentials(key: KeyVault, tierStore: SecretStore, pacer: GeminiPacer) { suspend fun tier(): String?; suspend fun setTier(t: String?) }
class OpenRouterProvider(transport: OpenRouterTransport)                               // Billing.OPENROUTER
class OpenRouterTransport(token: suspend () -> String?, client, clock) { suspend fun models(); suspend fun key(): OpenRouterKey }
class OpenRouterAuth(vault: KeyVault, http, random, label = "Lani", pause) { suspend fun begin(port: Int = 0): SignIn; suspend fun signOut(): String }
class LoopbackServer(bindPort: Int, path: String, threadName: String, handle: (Hit) -> Answer)  // ChatGPT's and OpenRouter's
```

**How the live tutor uses it.**

1. Pick the provider for the thread's model with `ProviderId.of(model)`. A thread never changes its model or its
   provider. A thread made under another `prefixHash` restarts as text: an app update changed a tool or the prompt,
   or the OpenAI credential changed, which changes how the tools are sent.
2. Build the turn's content as neutral blocks:
   - text: `textBlock(…)`;
   - a photo: `ShadowImages.block(file)`, the Messages API's base64 image shape. OpenAI turns it into an
     `input_image`.
3. Add the blocks with `provider.format.append(thread, blocks, top, want)`. With a `want` other than the level in
   effect, the format places the effort change: Anthropic's effort-only system message, or OpenAI's
   `configuration_update` (GPT-6 models). Only the thread's unanswered end ever changes.
4. Run `TutorLoop(provider, tools).run(…)`. Each step's native items are appended and `save`d. Tool calls reach the
   runner as `ToolUse`. A call's numbers reach `onCall` as `CallOutcome(step, error)`. Price them with
   `Prices.priced(model, usage, provider.billing)`: with the ChatGPT plan the cost is $0 and the API-equivalent price
   is kept beside it.
5. Errors are `ApiError`, the same for both providers:

   | Class | Meaning | What the app does |
   |---|---|---|
   | `Auth` | key refused, sign-in ended | ask for a new credential |
   | `LowCredit` | the credit is out | wait |
   | `LowCredit(limit)` | a spend limit was reached | wait |
   | `LowCredit(allowance)` | the ChatGPT plan's usage limit | link to ChatGPT's Usage settings |
   | `RateLimited` | too many requests | retry after `retry-after` |
   | `Overloaded`, `Network` | busy or offline | back off and retry |
   | `BadRequest` | the request is wrong | no retry |

   A refusal is `LoopEnd.Refused`, never an error.

## OpenAI: the Responses API

`POST https://api.openai.com/v1/responses`, `stream: true`, `store: false`, `Authorization: Bearer <API key or
ChatGPT access token>`.

- **The stable prefix:**
  - the core prompt goes in `instructions` (on the plan's route, system-role messages are rejected);
  - the tools go in `tools`, the same 17 in phase A's order;
  - `prompt_cache_key` is `lani-tutor-<prefix hash>`.

  Caching is automatic past 1,024 tokens. On GPT-5.6 and later, the implicit breakpoint sits at the latest user
  message or tool output, so the whole thread is cached and kept at least 30 minutes.
- **Tools:** the bridge's JSON schemas as function tools with `strict: false`:
  - Strict mode needs every property required and `additionalProperties: false` on every object. Most of the phase A
    schemas don't meet that (`OpenAIProvider.strictFits`; the test checks it).
  - Our client validates every input anyway (`Schema.check`), as for Anthropic.
  - No schema uses a keyword neither mode takes (`allOf`, `not`, `if`).
  - With the ChatGPT plan the tools go in one `namespace` (`lani`), as the plan's preview limitations ask.
- **Effort:** `reasoning.effort` at the levels each model takes (`ModelCatalog.openAIEfforts`, from the model pages):

  | Model | Efforts | Default |
  |---|---|---|
  | GPT-6 Astra | low, medium, high, xhigh, max | not documented |
  | GPT-6.1 Sol | low, medium, high, xhigh, max | medium |
  | GPT-6 Luna, GPT-6 Sol, GPT-5.6 | none, low, medium, high, xhigh, max | medium |
  | GPT-5.5 | none, low, medium, high, xhigh | medium |

  `max_output_tokens` is sent with an API key only: the plan's route rejects it.
- **Reasoning items:** with `store: false` the reasoning items come with `encrypted_content`. We also send
  `include: ["reasoning.encrypted_content"]`: the docs call it the legacy way, still accepted.
  - The thread keeps every output item as the `response.output_item.done` / `response.completed` events have it.
    The `added` event's encrypted content "may be incomplete".
  - A message keeps its `phase` ("preserve and resend phase on all assistant messages").
  - Items are replayed in order, before the tool results, on the next call and on the next turn.
  - A reasoning item without encrypted content is dropped: with `store: false` it can't be resolved.
- **Tool results:** a `function_call_output` by `call_id`. The Responses API has no error flag, so a failed result
  starts with "Error: ".
- **The stream** (`ResponsesAccumulator`), read line by line: the plan's route may send a stream without a content
  type.
  - Content deltas (text, function arguments, reasoning summaries, refusals) time the first token.
  - Only `response.completed` or `response.incomplete` counts as a finished response.
  - `incomplete_details.reason: max_output_tokens` → `max_tokens` (the loop retries once with twice the room);
    `content_filter` → refusal; a refusal content part → refusal; a `response.failed` for a policy → refusal.
- **Usage:** `input_tokens` counts the cached tokens and the tokens written to the cache too. So `Usage.input` is the
  rest, `cacheRead` is `cached_tokens`, `cacheWrite5m` is `cache_write_tokens` (OpenAI's one lifetime of at least 30
  minutes), and `reasoning` is `output_tokens_details.reasoning_tokens`. The thinking count is then exact
  (`CallRecord.thinkingEstimate`), not estimated.
- **Images:** a chat's photo goes as `{"type": "input_image", "image_url": "data:image/jpeg;base64,…", "detail":
  "auto"}`. The export keeps only a note of its size.
- **Errors** (`OpenAIErrors`):

  | Response | Class |
  |---|---|
  | 401; 403 (plan not allowed, region, permission) | `Auth` |
  | 403 `subscription_sharing_route_not_supported` | `BadRequest` |
  | 429 `credit_balance_exhausted` or `insufficient_quota` | `LowCredit` |
  | 429 organization or project spend limit, organization usage limit | `LowCredit(limit)` |
  | 429 `subscription_sharing_usage_limit_exceeded` | `LowCredit(allowance)` |
  | any other 429 (incl. `slow_down`) | `RateLimited` (after `retry-after`) |
  | 408, 409, 5xx (`server_is_overloaded`, the plan's usage or user "unavailable") | `Overloaded` |
  | other 4xx (`subscription_sharing_unsupported_capability`, context too long) | `BadRequest` |

  Errors in the stream are classified by their code the same way. With the plan, a 401 renews the sign-in once and
  the call is tried again.
- **Guards:**
  - The bearer goes to `https://api.openai.com` only (`OpenAIGuard`); the sign-in's tokens to `https://auth.openai.com`
    only.
  - `Redact` takes OpenAI keys (`sk-…`) and JSON Web Tokens out of log lines and the export, besides the known values
    (`OpenAICredentials.secrets()`).
  - The settings and the export hold no credential and no account e-mail.

## "Sign in with ChatGPT"

OpenAI's guide for open-source apps ("ChatGPT plan usage"; opened at DevDay on 2026-09-29, per the brief: the pages
read don't give a date). OAuth 2.0 with PKCE and OpenID Connect against auth.openai.com.

- **Registration:** the first sign-in registers the app as a client of the learner's account and workspace
  (`client_id=dynamic_agent_client`, `agent_name_hint=Lani`). There is no client secret. The issued client id
  (`oaiapp_…`) is kept at once, and reused afterwards with the account's e-mail as `login_hint`.
- **The host:** a stable `ext_agent_host_id` (`urn:uuid:…`) per installation. It is made before the first sign-in and
  kept through sign-outs.
- **Scopes:** `openid profile email offline_access resource.invoke chatgpt.tokens.use.direct`, with `resource` set to
  `https://api.openai.com/v1`. Calls are made only when the token response granted `chatgpt.tokens.use.direct`. The
  panel says so when the scope wasn't granted.
- **The redirect, on Android:**
  - The guide allows only an HTTP loopback callback on 127.0.0.1, with the path fixed and the port free to vary. No
    custom scheme, no app link.
  - So the app starts a listener on 127.0.0.1 (`Loopback`, a free port, path `/auth/callback`) and opens the system
    browser with an `ACTION_VIEW` intent. Custom Tabs isn't a dependency.
  - The listener answers one GET with the attempt's `state`. Stray requests get a 404, another state a 400: neither
    ends the attempt.
  - Its page sends the browser on to `lani://tutor/signed-in`, a new BROWSABLE intent filter of MainActivity that
    only brings the app to the front. The page also has a "↩ Nazaj v Lani · Back to Lani" button, in case the
    browser doesn't follow the redirect without a tap.
- **The exchange:** a form POST to `https://auth.openai.com/api/accounts/oauth/token` with the code, the PKCE
  verifier, the same redirect URI and the resource. The ID token is then checked: RS256 against
  `https://auth.openai.com/.well-known/jwks.json`, the issuer, the audience (the issued client id), the expiry, the
  nonce and the subject. A sign-in that returns another account than the one kept is refused.
- **Tokens:**
  - Access tokens last 1 hour; refresh tokens 30 days, and each refresh returns a replacement.
  - `accessToken()` refreshes when less than a minute is left, one refresh at a time (a `Mutex`). It keeps the
    replacement before using it and respects `earliest_refresh_at`.
  - An unusable refresh token (`invalid_grant`, `refresh_token_reused`, …) clears the tokens: sign in again. The
    client and the host stay.
  - A 5xx or the network keeps everything.
- **Sign out:** the refresh token is revoked at `https://auth.openai.com/api/accounts/oauth/revoke` (from the OpenID
  discovery document), then the tokens are cleared. If the revocation isn't confirmed, the panel says to disconnect
  Lani in ChatGPT's settings.
- **Storage:** the session is JSON, encrypted with a Keystore key of its own (`lani-tutor-chatgpt`) in the same
  DataStore as the Claude key (entry `chatgpt_session_sealed`). The OpenAI API key has its own key and entry too
  (`lani-tutor-openai-key`, `openai_key_sealed`).
- **The models:** `GET /v1/models` with the access token gives the account's catalog (`models[]`: `slug`,
  `display_name`, `visibility`). With an API key the same route gives the usual `data[]`. Both shapes are parsed.

**The terms, in brief:**
- ChatGPT plan usage is for "open-source and locally hosted apps". A paid or remotely hosted app needs OpenAI's
  approval first (the interest form). Lani is AGPL-3.0, and the loop runs on the learner's own phone.
- It needs ChatGPT Plus or Pro. Usage comes out of the learner's plan or credits, shared with every other app on the
  plan (Codex among them, per the brief). On Plus, the five-hour limit is shared across apps; Pro has none.
- The learner sees the usage and can set a limit per app at chatgpt.com/settings/usage. The panel's "Manage usage"
  opens that page.
- The UI guidelines ask for the label "Continue with ChatGPT" and "Using ChatGPT plan" where the plan is in use. The
  panel uses both.
- Links:
  - [overview](https://developers.openai.com/siwc/token-sharing-open-source)
  - [sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
  - [accounts and sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)
  - [errors](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
  - [preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
  - [UI/UX guidelines](https://developers.openai.com/siwc/ui-ux-guidelines)
  - [the cookbook article](https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt)
  - [OpenAI's policies](https://openai.com/policies/)

## Models, lanes, prices

**Families** (`Family`, small / mid / top), from the models page ("GPT-6 Astra, our flagship … GPT-6.1 Sol to
balance intelligence and cost, or GPT-6 Luna for cost-sensitive, high-volume workloads"):

| Tier | Anthropic | OpenAI |
|---|---|---|
| small | Haiku · `claude-haiku-5-5` | Luna · `gpt-6-luna` |
| mid | Sonnet · `claude-sonnet-5-5` | Sol · `gpt-6.1-sol` (then `gpt-6-sol`) |
| top | Opus · `claude-opus-5-5` | Astra · `gpt-6-astra` |

A family resolves to its own model in its provider's catalog, never another provider's.

**OpenAI's default lanes** take the same places as Anthropic's nine:
- Luna at medium, high, xhigh and max;
- Sol at low, medium and high;
- Astra at low and medium.

They join the matrix once, the first time an OpenAI credential is set (`MatrixSettings.openaiOffered`); a lane toggled
off later stays off. The grid offers every effort a model takes, Luna's `none` too. A lane's thread restarts past its
tier's limit (20k, 30k, 60k tokens). An OpenAI credential refused or out pauses only the OpenAI lanes.

**Prices** (per million tokens; `companion/tutor/prices.json`, from
[developers.openai.com/api/docs/pricing](https://developers.openai.com/api/docs/pricing), Standard tier, checked
2026-10-18; the model pages agree):

| Model | Input | Cached input | Cache writes | Output |
|---|---|---|---|---|
| gpt-6-astra | $10.00 | $1.00 | $12.50 | $50.00 |
| gpt-6.1-sol | $2.00 | $0.10 | $2.50 | $10.00 |
| gpt-6-sol | $2.00 | $0.20 | $2.50 | $10.00 |
| gpt-6-luna | $0.10 | $0.01 | $0.125 | $0.50 |

- Cache writes cost 1.25 times the input rate on GPT-5.6 and later.
- Prompts over 272K tokens cost 2 times the input and cache rates and 1.5 times the output (our prompts are about
  35k).
- Luna is priced as Haiku 5.5 and Sol 6.1 as Sonnet 5.5, but Sol reads its cache at half Sonnet's rate and OpenAI's
  cache lasts at least 30 minutes, not 5.

**With the ChatGPT plan** the learner pays $0 a token. Every call still records its tokens, and
`CallRecord.apiEquivalent` holds the API price, labelled as such everywhere:
- in the panel: "$0.0000 (ChatGPT plan; ≈$0.0123 API-equivalent)";
- in `DayStats.apiEquivalent` and `planCalls`;
- in `shadow-compare`'s "≈API/day" column and each turn's head line.

**What the OpenAI lanes would cost on an API key** (an estimate, scaled by price from the Anthropic lanes' "from the
code" numbers in 05-phase-0.md, "The matrix"):

| Lanes | A day |
|---|---|
| Luna, each | ~$0.05–0.12 |
| Sol, each | ~$0.45–0.65 (OpenAI's longer cache helps) |
| Astra, each | ~$2.5–3 |
| All nine | ~$7–8 |

On the ChatGPT plan that is $0, but nine lanes take about 450 requests a day out of the plan's allowance. On Plus
that may reach the five-hour limit. The `allowance` pause handles it, and the learner can set Lani's own limit in
ChatGPT's settings.

## Verified against OpenAI's docs (2026-10-18)

| What | Where |
|---|---|
| The ChatGPT plan's OAuth flow: endpoints, parameters, loopback-only redirect, PKCE S256, the issued `client_id`, the host id formats, the token response, the ID-token checks, credential storage | [sign-in](https://developers.openai.com/siwc/token-sharing-open-source/sign-in), [overview](https://developers.openai.com/siwc/token-sharing-open-source) |
| Token lifetimes (1 h access, 30-day rotating refresh, `earliest_refresh_at`) | [token reference](https://developers.openai.com/siwc/token-sharing-open-source/token-reference) |
| Refresh (form POST, the issued client, `resource`, no `scope`, serialized), revocation, the five-hour limit shared on Plus | [accounts and sessions](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions) |
| The plan's error codes and their recovery | [errors and recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery) |
| `store: false` and `stream: true` required, the ~15 unsupported fields (`max_output_tokens`, `temperature`, `top_p`, `prompt_cache_retention`, `truncation`, `safety_identifier`, `user` …), no system messages, tools in namespaces, no hosted tools, image input | [preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations) |
| The plan's model catalog (`models[]`, `visibility: "list"`), `response.completed` as the only success | [models and inference](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference) |
| The endpoints from the discovery document (revocation, JWKS, RS256, `none` auth for public clients) | [openid-configuration](https://auth.openai.com/.well-known/openid-configuration) |
| Function tools (flat shape, `strict`, the default normalization, results as `function_call_output`), namespaces, parallel calls, the streamed events of a call | [function calling](https://developers.openai.com/api/docs/guides/function-calling) |
| Effort levels, encrypted reasoning by default with `store: false` (`include` legacy but accepted), passing reasoning back with tool outputs, `configuration_update`, `phase` | [reasoning](https://developers.openai.com/api/docs/guides/reasoning) |
| Implicit caching, 1,024-token minimum, `prompt_cache_key`, `prompt_cache_options` (30 minutes), cache-write billing at 1.25x, `cache_write_tokens` | [prompt caching](https://developers.openai.com/api/docs/guides/prompt-caching) |
| The streaming events | [streaming](https://developers.openai.com/api/docs/guides/streaming-responses), the [API reference](https://developers.openai.com/api/reference/resources/responses) |
| `input_image` with a data URL, `detail` | [images and vision](https://developers.openai.com/api/docs/guides/images-vision) |
| HTTP errors and codes (`credit_balance_exhausted`, the spend limits, `slow_down`, `server_is_overloaded`, `retry-after`) | [error codes](https://developers.openai.com/api/docs/guides/error-codes) |
| Model ids, efforts, defaults, prices | [models](https://developers.openai.com/api/docs/models), the model pages (`/api/docs/models/gpt-6-astra`, `gpt-6.1-sol`, `gpt-6-luna`, `gpt-6-sol`, `gpt-5.6-*`, `gpt-5.5`), [GPT-6 guide](https://developers.openai.com/api/docs/guides/latest-model), [pricing](https://developers.openai.com/api/docs/pricing) |
| Replaying output items as input (only `created_by` and parsed values stripped); the type shapes (`NamespaceTool`, `function_call.namespace`, `usage.input_tokens_details.cache_write_tokens`, the encrypted content of `output_item.done`) | openai-node's [`ResponseInputItems.ts`](https://github.com/openai/openai-node/blob/master/src/lib/responses/ResponseInputItems.ts) and [`responses.ts`](https://github.com/openai/openai-node/blob/master/src/resources/responses/responses.ts) |
| The reference client's choices: no `id_token_hint` in URLs, the callback's checks, the stream without a content type, refresh checkpoints | OpenAI's [sign-in-with-chatgpt-devkit](https://github.com/openai/sign-in-with-chatgpt-devkit) (`packages/local/src/oauth.ts`, `models.ts`, `responses.ts`) |

platform.openai.com and openai.com/api/pricing answered 403. Everything above is from developers.openai.com (its `.md`
pages), the discovery document and OpenAI's own repositories.

## Not verified (built to the docs' shapes)

- **The plan's route was never called** (no real calls tonight). These are not confirmed:
  - that it takes `include: ["reasoning.encrypted_content"]` and `prompt_cache_key` (neither is on the unsupported
    list);
  - that a namespaced function call comes back with a bare `name` and a `namespace` field (as the SDK's types have
    it);
  - that it needs the namespace at all. The preview says "group function/custom tools in namespaces"; the API key's
    route sends plain function tools, the documented shape.
- **The exact JSON of an HTTP error body.** The guides name the codes, not a full body. We parse
  `{"error": {message, type, param, code}}`, `{"error": "code"}` and the plan's `{"detail": "…"}`. The
  `invalid_api_key` code string is also unconfirmed: any 401 is `Auth`.
- **Keep-alive events during a long reasoning pause** aren't documented. The client waits up to 600 s for a read.
- **GPT-6 Astra's default effort** isn't on its page. We always send one.
- **Whether earlier turns' reasoning is used on Astra and Luna.** The docs name the GPT-5.6 family and GPT-6.1 Sol for
  `all_turns`. We replay everything; the API leaves out what a model can't use.
- **`configuration_update`** is built and tested in the format only. Nothing uses it yet: the lanes keep one effort and
  the primary is Anthropic's.
- **Android, end to end:** the sign-in through a real browser to 127.0.0.1 and back to the app (`lani://tutor/…`), and
  whether Chrome follows the page's redirect without a tap (the button is there either way). The JVM tests play the
  browser with a socket. Not run on the emulator tonight.
- **The plan's catalog for Jan's account:** which GPT-6 models it lists depends on the account.

## Gemini: the Gemini API, direct

`tutor/Gemini.kt`. A key from Google AI Studio (aistudio.google.com/apikey), kept in its own KeyVault
(`lani-tutor-gemini-key`, `gemini_key_sealed`), and the key's tier beside it (`gemini_tier`).

**The request:** `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse`,
the key in `x-goog-api-key` (never in the URL). Google's docs now call generateContent "legacy" and recommend their
new Interactions API; generateContent "remains fully supported". The body:
- `systemInstruction`: the core prompt;
- `contents`: the thread;
- `tools`: one `functionDeclarations`, the bridge's JSON schemas as `parametersJsonSchema`, in the prefix's order. The
  schemas use `additionalProperties` and `anyOf`, which JSON Schema mode takes; none uses `$ref`;
- `toolConfig.functionCallingConfig.mode: AUTO`;
- `generationConfig`: `maxOutputTokens` (thinking counts toward it on Argon) and `thinkingConfig.thinkingLevel`,
  written lowercase as the docs' REST examples have it. No `temperature`, `top_p` or `top_k`: Argon ignores them, and
  from 3.6 Flash on they are deprecated.

The model goes in the URL; the provider's request carries it as `model` for the transport, which leaves it out of the
body. The read timeout is 20 minutes ("Set HTTP client timeouts to at least 20 minutes per call": Argon).

**The thread** (`GeminiFormat`): `{"role": "user" | "model", "parts": […]}`.
- A model turn is kept exactly as it came: its parts in order, each `thoughtSignature` where it was. Only plain text
  parts are joined while the stream is put together; a part with a signature is never merged with another ("Don't
  concatenate parts with signatures together"). "When using Gemini 4 and Gemini 3 models, you must pass back thought
  signatures during function calling, otherwise you will get a validation error"; on parallel calls "the
  thought_signature is attached only to the first functionCall part". The last turn's empty text part with its
  signature is kept too (recommended, not enforced).
- A tool's result is a `functionResponse` part in one user turn, by the call's `id` ("Gemini 3 now always returns a
  unique id with every functionCall. Include this exact id in your functionResponse"), with `{"output": …}` or
  `{"error": …}`.
- A call that came without an id gets one of ours (`lani_gc_<response>_<i>`), kept beside the parts (`lani_ids`) and
  never sent; its result then goes without an id.
- A photo is an `inlineData` part (`image/jpeg`, base64). The export keeps a note of its size.

**Thinking** (`ModelCatalog.geminiLevels`; the thinking guide and the model pages):

| Model | Levels | Default |
|---|---|---|
| `gemini-4-argon` | low, medium, high ("minimal" is an error; thinking can't be turned off) | medium |
| `gemini-3.8-flash` | low, medium, high ("minimal" is an error) | medium |
| `gemini-3.6-flash` | minimal, low, medium, high | not checked |
| `gemini-3.5-flash-lite`, `gemini-3.1-flash-lite` | minimal, low, medium, high | minimal |

A preset's level is fitted (`xhigh` and `max` go as `high`). **Per turn within a thread:** `thinkingLevel` is a request's
own field, so a thread could change it from one request to the next, but Google's docs don't say whether that is
allowed or what it does to the implicit cache (OpenRouter's reasoning guide: a request-level change "invalidates the
prompt cache"). So a Gemini thread keeps its level, as a Claude model without per-message effort does
(`ModelCatalog.perMessageEffort` is false); another task's level is a stateless call on its own.

**Caching:** implicit, on by default from Gemini 2.5, at least 4,096 tokens on Argon and 3.8 Flash; the stable prefix
(the prompt, the tools in a fixed order) comes first in every request. Hits are reported as `cachedContentTokenCount`.
Argon has no explicit caching ("Explicit caching is not supported"): none is used.

**Usage** (the API reference's UsageMetadata): `promptTokenCount` includes the cached part, so `Usage.input` is the rest
(plus `toolUsePromptTokenCount`, none here) and `cacheRead` the cached; `thoughtsTokenCount` is apart from
`candidatesTokenCount` ("prompt + tool-use prompt + thoughts + response candidates") and billed as output, so `output` is
both and `reasoning` the thoughts. Implicit caching bills no writes.

**Finish reasons:** `STOP` (a turn's end, or `tool_use` with calls), `MAX_TOKENS`; `SAFETY`, `RECITATION`, `LANGUAGE`,
`BLOCKLIST`, `PROHIBITED_CONTENT`, `SPII` and the image ones, and `promptFeedback.blockReason`, are a refusal;
`MALFORMED_FUNCTION_CALL`, `UNEXPECTED_TOOL_CALL`, `TOO_MANY_TOOL_CALLS`, `MALFORMED_RESPONSE` are made again (the loop's
backoff); `MISSING_THOUGHT_SIGNATURE` is a bad request (our bug).

**Errors** (`GeminiErrors`; the body `{"error": {code, message, status, details}}`, also array-wrapped):

| Response | Class |
|---|---|
| 400 with `API_KEY_INVALID` ("API key not valid"), 401, 403 (permission, a key reported as leaked) | `Auth` |
| 400 `FAILED_PRECONDITION` ("free tier is not available in your country. Please enable billing") | `LowCredit(limit)` |
| 402 ("Your Prepay credit balance is depleted") | `LowCredit` |
| 429 with a day's quota (`PerDay`, a retry delay of an hour or more), or "limit: 0" (a model without free quota on the key: Argon on the free tier) | `LowCredit(limit)` |
| any other 429 | `RateLimited`, after RetryInfo's `retryDelay` or `retry-after` |
| 408, 499, 500, 503 ("temporarily overloaded"), 504 | `Overloaded` |
| the rest (400 `INVALID_ARGUMENT`, 404) | `BadRequest` |

### The free tier and privacy

- **Which tier the key is on:** no API tells it (Google documents none; `usageMetadata.serviceTier` says standard, flex
  or priority). So the learner says so in the Tutor section ("Brezplačna raven · Free tier" or "Plačljiva (obračun
  vklopljen) · Paid (billing on)"). A 429 whose error names the free tier's quota (`generate_content_free_tier_requests`,
  a `FreeTier` quota id) marks the key as free too (`free-seen`), and the section says Google's answer showed it.
- **What the app says:** while the key is on the free tier, the Tutor section shows, in the error colour: "Na
  brezplačni ravni lahko Google uporabi, kar pošlješ, in odgovore za izboljšanje svojih izdelkov, in to lahko berejo
  ljudje. Ne pošiljaj osebnih ali občutljivih podatkov. … · On the free tier Google may use what you send, and the
  answers, to improve its products, and people may read them. Don't send personal or sensitive information. Gemini 4
  Argon isn't available there, and calls go more slowly (the free tier's limits)." The chat says it once
  (`tutorLive.note.geminiFree`), and its AI line adds "brezplačna raven Gemini · Gemini free tier".
- **Google's terms** (the Gemini API Additional Terms of Service, [ai.google.dev/gemini-api/terms](https://ai.google.dev/gemini-api/terms),
  "Effective March 23, 2026", read 2026-10-19):
  - "When you use Unpaid Services, including, for example, Google AI Studio and the unpaid quota on Gemini API,
    Google uses the content you submit to the Services and any generated responses to provide, improve, and develop
    Google products and services and machine learning technologies, including Google's enterprise features, products,
    and services, consistent with our Privacy Policy."
  - "To help with quality and improve our products, human reviewers may read, annotate, and process your API input
    and output. … Do not submit sensitive, confidential, or personal information to the Unpaid Services."
  - Paid Services: "Google doesn't use your prompts (including associated system instructions, cached content, and
    files such as images, videos, or documents) or responses to improve our products", and logs them "for a limited
    period of time, solely for detecting and preventing violations of the Prohibited Use Policy".
  - "Your access to Gemini API is a 'Paid Service' only when accessing the API through a Cloud Project associated
    with an active billing account."
- **For the lead (not settled tonight):** the same terms say "If you're in the European Economic Area, Switzerland, or
  the United Kingdom, the terms under 'How Google uses Your Data' in 'Paid Services' apply to all Services, including
  Google AI Studio and unpaid quota in the Gemini API", and "You may use only Paid Services when making API Clients
  available to users in the European Economic Area, Switzerland, or the United Kingdom". Jan is in Slovenia. The app's
  note says what Google *may* do on the free tier, which is the cautious reading; whether Lani may offer the free tier
  to learners in the EEA at all (the learner's own key in an app offered to them) needs a decision. The terms also say
  the API is for "professional or business purposes, not for consumer use", and 18+.
- **The pace** (`GeminiPacer`, one per key: the quota is the project's, so the primary, the studio and the lanes share
  it): the free tier's limits per model aren't published ("can be viewed in Google AI Studio"; the lead read 5–15
  requests a minute and about 1,000 a day; a forum reports 20 a day for 3.8 Flash). On the free tier a call waits for
  its slot, one every 6 s; a 429 doubles the gap (at most a minute) and waits its retry delay; calls that go through
  ease it back by a quarter. The loop's own retry (after the retry delay, at most 30 s, three times) comes on top; a
  day's quota is `LowCredit`, not retried: the tutor pauses until the learner checks again or a new day.
- **No Argon on the free tier** ("Free tier: Not available"): the presets' top tier maps to Flash there
  (`LiveTutor.modelFor`), the matrix skips Argon's lanes, and a call that still gets "limit: 0" pauses as
  `LowCredit(limit)`.
- **Its price:** a free-tier call costs $0 (`Billing.FREE_TIER`); its API-equivalent is recorded as the ChatGPT plan's
  is, so the caps don't stop it and the numbers still compare.

### Models and prices

From [ai.google.dev/gemini-api/docs/models](https://ai.google.dev/gemini-api/docs/models) and
[pricing](https://ai.google.dev/gemini-api/docs/pricing) (Standard, paid tier, per million tokens, checked 2026-10-19):

| Model | Family | Free tier | Input | Cached input | Output (thinking included) |
|---|---|---|---|---|---|
| `gemini-4-argon` | ARGON (top) | no | $2.00 through 2026-11-30, then $4.00 | $0.10, then $0.20 | $10.00, then $20.00 |
| `gemini-3.8-flash` | FLASH (mid) | yes | $0.75 through 2026-12-31, then $1.50 | $0.075, then $0.15 | $3.75, then $7.50 |
| `gemini-3.6-flash` | (Flash's fallback) | yes | as 3.8 Flash | as 3.8 Flash | as 3.8 Flash |
| `gemini-3.5-flash-lite` | FLASH_LITE (small) | yes | $0.30 | $0.03 | $2.50 |

- `prices.json` has a row per span of days (`from`, `until`): `Prices` takes the row of the call's day (UTC).
- No long-context tier on these three (only the Pro models have one). Thought signatures sent back count as input.
- The lead's "gemini-3.8-flash" is the real id.

## OpenRouter: one sign-in, every model

`tutor/OpenRouter.kt`. The learner's own key, from OpenRouter's sign-in, billed to their OpenRouter credits; kept in
its own KeyVault (`lani-tutor-openrouter-key`, `openrouter_key_sealed`).

**"Nadaljuj z OpenRouter · Continue with OpenRouter"** (`OpenRouterAuth`; [the OAuth guide](https://openrouter.ai/docs/guides/overview/auth/oauth)):
1. The shared loopback listens on 127.0.0.1 (port 0: the system's pick; the redirect names the port it got) at
   `/auth/openrouter`.
2. The browser opens `https://openrouter.ai/auth?callback_url=http://127.0.0.1:<port>/auth/openrouter&code_challenge=…&code_challenge_method=S256&state=…&key_label=Lani`.
   The API reference takes "localhost/127.0.0.1 URLs on any port"; `state` is "Returned unchanged as a state query
   parameter on the callback URL"; `key_label` "Prefills the label of the API key".
3. The callback: another `state` gets a 400, a stray request a 404 (neither ends the attempt); no code and no state is
   a decline ("Not returned when the user denies access"). The page sends the browser back to the app
   (`lani://tutor/signed-in`).
4. The exchange: `POST https://openrouter.ai/api/v1/auth/keys` with `{code, code_verifier, code_challenge_method}`,
   answered `{"key": "sk-or-v1-…", "user_id": …}`. "The code is single-use and expires after 10 minutes": the attempt
   waits 10 minutes at most. While the network isn't there the exchange is tried again for about a minute
   (`WhileOffline`, ChatGPT's `OFFLINE_WAITS`: Lani is in the background while the browser comes back); a 403 ("Invalid
   code or code_verifier", "Authorization code expired") is told, not tried again.
5. The check: `GET /api/v1/key` (the key's label, limit, `limit_remaining`, usage): the Tutor section shows what's left.
6. **Sign out** forgets the key on the phone. OpenRouter has no revocation for a user's key from an app (deleting a key
   needs a management key), so the section links to openrouter.ai/settings/keys to delete it there.

**The request** (`OpenRouterProvider`; [chat completions](https://openrouter.ai/docs/api_reference/overview),
[tool calling](https://openrouter.ai/docs/guides/features/tool-calling)): `POST https://openrouter.ai/api/v1/chat/completions`,
`Authorization: Bearer <key>`, `HTTP-Referer: https://github.com/fentas/lani` and `X-OpenRouter-Title: Lani` (the
app's attribution).
- `messages`: the core prompt as the system message, then the thread; `tools`: function tools from the bridge's
  schemas, in the prefix's order; `tool_choice: auto` (Claude 5.5 refuses a forced tool there too); `stream: true`;
  `max_tokens`; `session_id` by the prefix (sticky routing, for the provider's cache).
- `reasoning.effort` where the model takes one: the model list's `reasoning.supported_efforts` (`RoutedModels`), else
  the vendor's own levels (Gemini's, OpenAI's, Claude's). OpenRouter maps effort onto Gemini's `thinkingLevel` and onto
  Anthropic's thinking budget (`budget_tokens = max(min(max_tokens × ratio, 128000), 1024)`).
- **Claude through OpenRouter:** the system prompt's text with `cache_control: {type: ephemeral, ttl: 1h}` and a
  top-level `cache_control` (the moving breakpoint), as the Messages API's request has them (OpenRouter passes
  Anthropic's breakpoints on, at most four, `ttl: "1h"` included). The others cache by themselves (OpenAI's, Gemini's
  implicit cache); no `cache_control` is sent to Gemini (explicit caching is an error on Argon at Google's).
- **The thread** (`ChatFormat`): the user's `content` parts (`text`, `image_url` with a data URL); the assistant's
  message as it came: `content`, `tool_calls`, and `reasoning_details` unchanged ("the entire sequence of consecutive
  reasoning blocks must match the outputs generated by the model during the original request"); a result is
  `{"role": "tool", "tool_call_id", "content"}` (no error flag: a failed one begins "Error: ").
- **The stream** (`ChatAccumulator`): `: OPENROUTER PROCESSING` comments skipped; `delta.content`, `delta.tool_calls`
  by `index` (the id and the name once, the arguments in pieces), `delta.reasoning_details` joined by `index` (text,
  summary and data in pieces, the last signature); `finish_reason` (it comes twice: the usage chunk "repeats the
  finish_reason"); the usage on the last chunk; `data: [DONE]`. A chunk's `error` ("The error can be the first and
  only event in the stream") is classified by its code. `length` is `max_tokens`, `content_filter` and a `refusal`
  are a refusal.
- **Per turn within a thread:** no ("Request-level effort applies to the whole conversation, so raising it for one
  hard turn and lowering it again on the next changes the request prefix and invalidates the prompt cache").

**Usage and cost** ([usage accounting](https://openrouter.ai/docs/cookbook/administration/usage-accounting)): it comes
always now ("The usage: { include: true } … parameters are deprecated and have no effect"). `prompt_tokens` counts the
cached and written tokens, so `input` is the rest, `cacheRead` `prompt_tokens_details.cached_tokens`, `cacheWrite5m`
its `cache_write_tokens`; `completion_tokens` includes the reasoning (`reasoning` its `reasoning_tokens`). Each call
is priced by `usage.cost` ("total amount charged to your account"), the table's vendor row only when it's missing.

**Errors** (`OpenRouterErrors`; [errors](https://openrouter.ai/docs/api_reference/errors-and-debugging),
[limits](https://openrouter.ai/docs/api_reference/limits)), the body `{"error": {code, message, metadata}}`:

| Response | Class |
|---|---|
| 401 ("OAuth session expired, disabled/invalid API key") | `Auth` |
| 402 `limit_source: openrouter_in_flight_budget` (passing, with Retry-After) | `RateLimited` |
| 402 `openrouter_key_limit` (the key's own limit) | `LowCredit(limit)` |
| 402 `openrouter_credits` ("insufficient credits") | `LowCredit` |
| 403 with a `limit_source` or "Budget limit exceeded" (a key, workspace or guardrail budget) | `LowCredit(limit)` |
| 403 moderation (`reasons`, `flagged_input`) | `BadRequest` (not retried) |
| other 403 (permissions) | `Auth` |
| 429 | `RateLimited` (after `retry-after`) |
| 408, 502 ("model is down"), 503 ("no available model provider"), 5xx | `Overloaded` |
| the rest (400) | `BadRequest` |

**The models** (`GET https://openrouter.ai/api/v1/models?supported_parameters=tools`, public: no key sent; ids from the
live list, 2026-10-19): the learner picks any model that takes tools, per task under "advanced". The tiers' defaults
(`OpenRouterModels.DEFAULTS`, a preset's small, mid, top): `openai/gpt-6-luna`, `google/gemini-3.8-flash`,
`google/gemini-4-argon`. Shown first: those, `google/gemini-3.5-flash-lite`, `openai/gpt-6.1-sol`,
`anthropic/claude-sonnet-5.5` (with a dot: OpenRouter's id), `google/gemma-4-31b-it`.
- **An open model with good Slovene:** none is documented on OpenRouter. Gemma 4 ("35+ languages, pre-trained on 140+"),
  Qwen 3.5 ("201 languages and dialects") and Mistral Small name many languages, not Slovene; CJVT's Slovene GaMS
  models are on Hugging Face, not on OpenRouter. So no open model is a default; Gemma 4 is in the list to try.
- **"Claude works best directly"** (the Tutor section says so): Lani's 1-hour cache and per-message effort are
  Anthropic features. OpenRouter passes `cache_control` with `ttl: 1h` on for Anthropic's models, but per-message
  effort isn't used through it.
- **Fees** ([FAQ](https://openrouter.ai/docs/faq)): "OpenRouter charges a 5.5% ($0.80 minimum) fee when you purchase
  credits. We pass through the pricing of the underlying model providers without any markup." Crypto: 5%. The Tutor
  section says so. (BYOK's fee doesn't apply: the key is OpenRouter's.)
- **Privacy:** "Prompt and completion are not logged by default" (OpenRouter's FAQ); the providers it routes to have
  their own data policies, which the learner sets in their OpenRouter account.

## The presets across providers (the live tutor)

- **A preset's tier per provider** (`LiveTutor.modelFor`): the provider chosen in the Tutor section plays each task's
  tier (small, mid, top: Economy, Balanced and Best as before):

  | Tier | Anthropic | OpenAI | Gemini | OpenRouter (defaults) |
  |---|---|---|---|---|
  | small | Haiku 5.5 | GPT-6 Luna | 3.5 Flash-Lite | `openai/gpt-6-luna` |
  | mid | Sonnet 5.5 | GPT-6.1 Sol | 3.8 Flash | `google/gemini-3.8-flash` |
  | top | Opus 5.5 | GPT-6 Astra | 4 Argon (Flash on the free tier) | `google/gemini-4-argon` |

  The preset's effort is fitted to the model (Gemini's `xhigh` and `max` are `high`; Flash-Lite takes `minimal`).
- **A task's model across providers:** under "advanced" a task can take a model of another provider whose credential
  is kept (the chat on the provider chosen, the content on Claude Opus or Gemini Argon); its calls go to that provider,
  priced by it. Without that credential the preset's model plays. Changing the provider clears the overrides, as before.
- **The studio** runs on the content task's model; only Anthropic's has the Message Batches API: on the others the
  night's jobs wait for charging on Wi-Fi.

## The loopback, shared

`tutor/Loopback.kt`: `LoopbackServer` is the HTTP listener on 127.0.0.1 that both sign-ins' browsers come back to (one
GET on its path with the Host `127.0.0.1:<port>`, a strict CSP, no outside resources, a page back to the app).
ChatGPT's `Loopback` is built on it with its own checks (the state, the issued client id), unchanged in behaviour
(`ChatGPTAuthTest` passes as before). The two fixes that came after this doc was first written are in it: the
redirect names the port the server socket got (the constructor's `bindPort` no longer shadows `port`), and
`WhileOffline` tries the exchange again while Android holds the background app's network.

## Lanes of Gemini and OpenRouter

- **Gemini's** (`MatrixSettings.GEMINI_LANES`): 3.8 Flash at medium, 4 Argon at low, medium and high
  (`gemini:gemini-4-argon@high`). They join once, when a Gemini key is first set (`geminiOffered`). On the free tier
  Argon's are skipped ("Gemini 4 Argon has no free tier") and Flash's cost $0 with their API-equivalent.
- **OpenRouter's**: a `LaneSpec(Family.ROUTED, effort, model)` per picked model (the grid's "+ a model", from its list:
  the suggestions, then the rest; at an effort the model takes, or `none` for one that takes none). At the first
  sign-in the tier defaults join at medium (`openrouterOffered`). Ids: `openrouter:google/gemini-4-argon@medium`; the
  directory replaces the "/" with "~" (`Lanes.dir`), so a lane is one directory (`matrix/openrouter:google~gemini-4-argon@medium`).
- A provider without its credential, a model the list lacks, an effort the model doesn't take: skipped, and the panel
  and the export say why. A credential refused or out pauses only its provider's lanes.
- The export carries them like the others (each day's `lanes` by id, `matrix.lanes` with `provider` and `model`), and
  the tiers (`gemini: {ready, tier}`, `openrouter: {ready}`; never a key). `shadow-compare` shows them as columns: a
  free-tier call "$0.0000 Gemini free tier (≈$… API)", an OpenRouter call "OpenRouter's price"; `--lanes gemini:` or
  `--lanes openrouter` (or a part of a model's id, `google/`).

## Verified against Gemini's and OpenRouter's docs (2026-10-19)

| What | Where |
|---|---|
| The model ids, their limits, levels, caching, the sampling parameters ignored, the 20-minute timeouts | [models](https://ai.google.dev/gemini-api/docs/models), `/models/gemini-4-argon`, `/models/gemini-3.8-flash`, `/models/gemini-3.5-flash-lite`, [latest model](https://ai.google.dev/gemini-api/docs/generate-content/latest-model), [what's new in 3.6](https://ai.google.dev/gemini-api/docs/generate-content/whats-new-gemini-3.6) |
| Prices, the dates, no free tier for Argon, thinking billed as output, signatures counted as input | [pricing](https://ai.google.dev/gemini-api/docs/pricing), [thinking](https://ai.google.dev/gemini-api/docs/generate-content/thinking) |
| The endpoint, `x-goog-api-key`, the request and response fields, UsageMetadata's definitions, FinishReason | [generate-content reference](https://ai.google.dev/api/generate-content), [text generation](https://ai.google.dev/gemini-api/docs/generate-content/text-generation) |
| Function calling, the ids, `parametersJsonSchema`, the modes | [function calling](https://ai.google.dev/gemini-api/docs/generate-content/function-calling) |
| Thought signatures: required back, the first parallel call's, strict for the current turn, no merging | [thought signatures](https://ai.google.dev/gemini-api/docs/generate-content/thought-signatures) |
| `thinkingLevel` per model, lowercase in REST, no `thinkingBudget` with a level | [thinking](https://ai.google.dev/gemini-api/docs/generate-content/thinking) |
| Implicit caching, its minimums, no explicit caching on Argon | [caching](https://ai.google.dev/gemini-api/docs/generate-content/caching) |
| The error codes, the invalid key's body, retry only 429, 408, 5xx | [API errors](https://ai.google.dev/gemini-api/docs/generate-content/api-errors), [troubleshooting](https://ai.google.dev/gemini-api/docs/troubleshooting) |
| Rate limits per project, RPD at midnight Pacific, no per-model free limits published | [rate limits](https://ai.google.dev/gemini-api/docs/rate-limits) |
| Image input, MIME types | [image understanding](https://ai.google.dev/gemini-api/docs/generate-content/image-understanding) |
| Unpaid and paid services, the EEA clauses | [terms](https://ai.google.dev/gemini-api/terms) |
| OpenRouter's PKCE: the URL, `state`, `key_label`, the exchange, the code's 10 minutes, localhost and 127.0.0.1 on any port | [OAuth](https://openrouter.ai/docs/guides/overview/auth/oauth), the API reference's `POST /auth/keys` |
| The attribution headers, the stream's comments and `[DONE]`, the usage chunk, a mid-stream error | [app attribution](https://openrouter.ai/docs/app-attribution), [streaming](https://openrouter.ai/docs/api_reference/streaming) |
| Tool calling's shapes | [tool calling](https://openrouter.ai/docs/guides/features/tool-calling) |
| `reasoning`, its efforts, the mapping to Gemini and Anthropic, `reasoning_details` passed back unchanged, the effort change clearing the cache | [reasoning tokens](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens), the [Gemini 4 migration note](https://openrouter.ai/docs/cookbook/evaluate-and-optimize/model-migrations/gemini-4) |
| `cache_control` for Anthropic (four breakpoints, `ttl: 1h`), Gemini's last breakpoint only, the cache fields | [prompt caching](https://openrouter.ai/docs/guides/best-practices/prompt-caching) |
| Usage always on, its fields, `cost` | [usage accounting](https://openrouter.ai/docs/cookbook/administration/usage-accounting) |
| The model list's fields, `supported_parameters`, `reasoning.supported_efforts` | [list models](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties) and the live list |
| The errors and `limit_source`; the key's own state | [errors](https://openrouter.ai/docs/api_reference/errors-and-debugging), [limits](https://openrouter.ai/docs/api_reference/limits) |
| The fees, no logging by default | [FAQ](https://openrouter.ai/docs/faq) |

## Not verified for Gemini and OpenRouter (built to the docs' shapes)

- **No real call tonight** to Gemini or OpenRouter, nor a real sign-in.
- **Gemini:**
  - the 429's details (QuotaFailure, RetryInfo) aren't in Google's docs; the shapes are from reports in the wild, and
    some 429s carry none. The free tier's detection from a 429 rests on them;
  - whether a long `functionCall` can be split across stream chunks (the accumulator expects each part whole);
  - whether changing `thinkingLevel` within a conversation is allowed and what it does to the implicit cache (not
    used);
  - `parametersJsonSchema` with our schemas as Google takes them (the docs: "The model ignores unsupported
    properties"); `$ref` is undocumented there (none used);
  - the error a monthly tier cap returns ("service is paused"), and Argon on a free key ("limit: 0" by reports);
  - the free tier's real limits on Jan's key (AI Studio shows them): the 6-second pace is a guess;
  - Flash-Lite's implicit-cache minimum;
  - the EEA question above.
- **OpenRouter:**
  - how `delta.tool_calls` and `delta.reasoning_details` come in pieces: the accumulator joins by `index`; a model
    whose details come whole keeps them whole;
  - that Gemini's thought signatures travel in `reasoning_details` (implied by `format: google-gemini-v1`, never
    stated);
  - Chrome on Android following the loopback's redirect to 127.0.0.1 and back (as for ChatGPT's sign-in, the page has a
    button too);
  - `cache_discount`, mentioned once in the docs, isn't read.

## Tests

All JVM, no network but the loopback in this round's port block (19400–19499):

- **`AnthropicWireTest` (4):** the Messages API's request, headers, betas and appended messages byte for byte. Written
  and passed before the refactor, passed after it.
- **`OpenAITest` (22):**
  - the stream, with a tool call and then a reply;
  - usage, with cached, written and reasoning tokens;
  - each error class: HTTP, in the stream, `response.failed`, a stream that stops early;
  - refusals (a refusal part, the content filter, a policy failure);
  - a stream without a content type; arguments that aren't JSON; a reasoning item without encrypted content;
  - `max_output_tokens`;
  - the loop over the real client: reasoning items going back untouched between tool calls and across turns,
    `store: false`, the request's fields with a key and with the plan;
  - the plan's 401 renewing once;
  - the guards;
  - images; `configuration_update` placement; pending calls;
  - the 17 tools' schemas (strict false, fit for neither strict mode nor any forbidden keyword);
  - efforts per model; families and lane ids.
- **`ChatGPTAuthTest` (7):**
  - the first sign-in: PKCE (the verifier's S256 is the challenge), the loopback's checks, the exchange's form, the ID
    token, the session encrypted;
  - refresh near expiry with rotation, one refresh for two callers, a forced one;
  - an unusable refresh token, and a passing failure;
  - sign out with revocation, and the next sign-in with the kept client and host;
  - declined, an incomplete registration, the plan's scope not granted;
  - bad ID tokens: nonce, audience, expiry, signature, issuer, subject;
  - the guards.
- **`ShadowTutorTest` (+5):**
  - OpenAI lanes waiting for a credential, then joining once;
  - each OpenAI lane on its own thread: every event, the Responses API's fields, prices by key, the export;
  - signing in through the panel's call with the browser played: plan billing at $0 with the API-equivalent, the
    namespace, no token in the export or a log line, sign out;
  - an OpenAI credential out pausing only the OpenAI lanes, then "check again";
  - "also run on" an OpenAI model.
- **`PricingTest` (+2):** OpenAI's rows, long prompts, the plan's pricing; a `Usage` without reasoning tokens written
  as before.
- **`companion/tests/test_shadow_compare.py` (+1):** OpenAI lanes, the plan's API-equivalent, `--lanes openai`.

**Numbers** (2026-10-18): the Android unit tests 2,620, 0 failed (the tutor's 143; 107 before). Python
`-s companion/tests` 49 and `-s tests` 106, all OK. The bridge isn't touched (`prices.json` isn't the bridge's).

**Gemini and OpenRouter** (2026-10-19), against fakes on MockWebServer in the block (19440 Gemini, 19450 OpenRouter),
the sign-ins' loopback on port 0:
- **`GeminiTest` (9):** a turn over the real client (two calls with their signature, then the reply; the URL with the
  model, the key in its header only, the body's fields; the model turn sent back exactly; results by id; usage with the
  cached part and the thoughts); levels fitted per model, none sent without one; a photo as `inlineData`, our ids for a
  call without one, never sent; each error class (the invalid key, a leaked key, billing off in the region, prepay,
  a minute's 429 with its delay, the free tier's day with its quota named, "limit: 0", a bare 429, 503, 500, 504, 404,
  an array-wrapped body); the finish reasons (a block, a cut-off, a botched call made again, an early end, an error in
  the stream); the pacer (the gap, the backoff after a 429, easing back, a paid key unpaced); the guard and `Redact`;
  the model list and the families; the prices by day (Argon on 2026-11-30 and 2026-12-01, Flash in January, the free
  tier's $0 and API-equivalent).
- **`OpenRouterTest` (9):** PKCE with the loopback on port 0 (the redirect names the real port; a stray request,
  another state and another Host never end it; the code with the verifier whose S256 is the challenge; the key kept;
  sign out); the exchange tried again while offline (three failed resolutions, then the key; the waits the
  `OFFLINE_WAITS`); offline for good, declined, a refused code; a turn over the real client (the request's fields and
  headers, `reasoning_details` joined and passed back unchanged, results as `tool` messages, usage and `cost`); Claude's
  `cache_control`, a model without efforts, `image_url`; the stream (a cut-off, the content filter, an error chunk, an
  in-stream 402, an early end); each error class; the model list without a key, its efforts and prices, the key's
  credit; the guard and `Redact`.
- **`OpenAIWireTest` (4):** the Responses API's request byte for byte with an API key and with the ChatGPT plan, the
  wire, the appended items: OpenAI's format stays as it was (`OpenAI.kt` is unchanged).
- **`ProviderLanesTest` (3):** lane ids and directories, the plan's skips, providers and formats by id, a routed
  model's vendor price.
- **`ShadowTutorTest` (+2), `LiveTutorTest` (+2):** above ("Lanes"; the live tutor on Gemini's free tier and on
  OpenRouter, through the browser played).
- **`companion/tests/test_shadow_compare.py` (+1):** Gemini's and OpenRouter's lanes, the free tier's API-equivalent,
  OpenRouter's price, `--lanes gemini:` and `openrouter`.
- `ChatGPTAuthTest` passes unchanged on the shared loopback; `AnthropicWireTest` too.

**Numbers** (2026-10-19): the Android unit tests 2,826, 0 failed (the tutor's 230; 198 before this round, the studio
fix's three included). Python `-s companion/tests` 68 and `-s tests` 249, all OK. The bridge isn't touched.

## What's left

- **The live tutor** builds on this loop (the next agent): the learner-facing setup ("Continue with ChatGPT" in the
  onboarding, the first-sign-in confirmation the UI guidelines ask for, "Using ChatGPT plan" by the chat), and
  OpenAI as a primary provider (presets per provider; `configuration_update` for a turn at another level on a GPT-6
  thread).
- **The first real calls:** check the "Not verified" list on Jan's ChatGPT plan with one lane (`--lanes openai`),
  then the defaults.
- **A QA step** (`companion/bin/qa`) for the sign-in on the emulator, and a Responses API mock next to
  `tutor-mock.ts` (that is the bridge's directory: not touched tonight).
- **Account switching:** the guide's account picker (several registrations). One account is kept for now; signing
  out and in with another is refused as "another account" until the session is cleared.
- **Gemini and OpenRouter, the first real calls:** one Gemini lane and one OpenRouter lane on Jan's keys, then the
  "Not verified" lists (the 429's details on the free tier, the routed stream's pieces, Gemini's signatures through
  OpenRouter).
- **The EEA question** (Gemini's free tier for a learner in the EEA): a decision for the lead and Jan.
- **Per-turn thinking on Gemini:** if Google documents that a level change keeps the implicit cache, a turn at another
  level could stay on the day's thread (a marker item, as OpenAI's `configuration_update`).
- **QA on the emulator:** the two sign-ins through a real browser (Chrome to 127.0.0.1 and back), and mocks of
  Gemini's and OpenRouter's APIs next to `tutor-mock.ts`.
- **The Interactions API:** Google's new default for Gemini; generateContent stays supported.
