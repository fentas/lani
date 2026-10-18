# Plan 5, built: more than one LLM provider

*Built 2026-10-18 (the overnight round, design point 6: "Anthropic as now, and OpenAI (API key, and 'Sign in with
ChatGPT') next to it"). Branch `providers`. Code: `companion/android/app/src/main/java/si/lanisce/lani/tutor/`
(`Provider.kt`, `Loop.kt`, `OpenAI.kt`, `ChatGPT.kt`), the panel `ui/tutor/ShadowPanel.kt`, `companion/tutor/prices.json`,
`companion/bin/shadow-compare`.*

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

## The provider API (for the live tutor)

Everything is in `tutor/Provider.kt`. The live tutor builds on these types:

```kotlin
enum class ProviderId { ANTHROPIC, OPENAI; companion object { fun of(model: String): ProviderId } }
object Billing { const val API_KEY = "api_key"; const val CHATGPT_PLAN = "chatgpt_plan" }
object Stop { END_TURN, TOOL_USE, MAX_TOKENS, REFUSAL }            // the loop's stop reasons (strings)

data class Step(                                                    // one call's response, normalized
    val items: List<JsonObject>,        // what the thread keeps, in the provider's own format
    val toolCalls: List<ToolUse>,       // ToolUse(id, name, input, badJson)
    val text: String, val stopReason: String?, val stopDetails: JsonObject?,
    val usage: Usage,                   // input, cacheWrite5m/1h, cacheRead, output, reasoning
    val model: String?, val id: String?, val firstTokenMs: Long?, val totalMs: Long, val visibleChars: Int,
    val kept: List<JsonObject>,         // what stays of a response cut off at max tokens
    val inputTransformations: String?, val rawUsage: RawUsage,
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

interface ThreadFormat {                                            // AnthropicFormat, OpenAIFormat
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
