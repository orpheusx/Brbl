# Opr8r Message Processing Flow
#### Generated 2026-07-17.
### 1. Entry Point: OperatorConsumer.handleDelivery

* Location: OperatorConsumer.java
* Execution:
  1. Deserializes the message from raw bytes to a Message object: Message.fromBytes(body).
  2. Invokes the core business logic processor: processor.process(message) (which maps to Operator.process).
  3. Evaluates the resulting ProcessStateSession object:
     * OK : Acknowledges the RabbitMQ delivery and writes to logs:
     * PersistenceManager Write: Calls persistenceManager.insertProcessedMO(message, session) to mark the mobile-originated (MO) message as
     processed.
     * ERROR : Publishes the message to the dead-letter / failed queue and acknowledges the original delivery.
     * RETRY : Computes retry delays and publishes to a RabbitMQ delay exchange.


──────
### 2. Core Processing: Operator.process

* Location: Operator.java
* Execution:
  1. Creates a SessionKey from the metadata in the Message.
  2. Retrieves or constructs the user's conversational session via sessionCache.get(sessionKey). (See Section 3 for cache-loader details).
  3. Evaluates if the user is brand new: isUserBrandNew.
  4. PersistenceManager Write: If isUserBrandNew == true , calls persistenceManager.insertNewUser(user) to persist the profile in the database.
  5. Selects the execution route based on normalized message text:
     * Opt-Out (Stop words): Calls handleOptOut(..).
       * PersistenceManager Write: Calls persistenceManager.updateUserStatus(sessionUser, platform, UserStatus.OUT).
       * Delegates message execution to ScriptEngine.process(..).
     * Change Topic Request: Calls handleChangeTopic(..).
       * Looks up the interrupt node (resolves using cached routes).
       * Delegates message execution to ScriptEngine.process(..).
     * Normal Message Flow: Delegates message execution to ScriptEngine.process(..).
  6. Checks if user opt-in status needs updating. If so:
     * PersistenceManager Write: Calls persistenceManager.updateUserStatus(sessionUser, platform, UserStatus.IN).


──────
### 3. Session Retrieval & Cache-Loading Phase

Triggered transparently during sessionCache.get(sessionKey) inside Operator.process(..) :

* findOrCreateSession Builder: Operator.java
1. Get/Create User: Calls userCache.get(sessionKey) which triggers findOrCreateUser(sessionKey) :
   * PersistenceManager Read: Calls persistenceManager.getUser(sessionKey) to fetch the profile details.
   * If User doesn't exist: Looks up route owners using findRoute(..) -> activeRoutesCache.get(ALL) -> triggers persistenceManager.getActiveRoutes()
   (Read).
2. Fetch Keyword Node: Calls scriptByKeywordCache.get(KeywordCacheKey.newKey(sessionKey)) which triggers findScriptForKeywordChannel(..) :
   * Calls allKeywordsByPatternCache.get(ALL) -> triggers persistenceManager.getKeywords() (Read).
   * If a keyword pattern matches, loads the target root node from scriptCache.get(nodeId) -> triggers persistenceManager.getNodeGraph(nodeId) (Read).
3. Fetch Default Node (if no keyword matches): Calls findDefaultScriptByRoute(..) which retrieves active routes (possibly calling persistenceManager.
getActiveRoutes() ) and loads the node via scriptCache.get(nodeId) -> triggers persistenceManager.getNodeGraph(nodeId) (Read).
4. Load Existing Session: Checks if the user is an existing user and attempts to load active sessions:
   * PersistenceManager Read: Calls persistenceManager.loadSession(user.groupId()).


──────
### 4. Execution Engine: ScriptEngine.process

* Location: ScriptEngine.java
* Execution:
  1. Registers the incoming Message under the session context ( session.registerInput(message) ).
  2. Loops and steps through the conversational graph using evaluate(session, message) until a node of type AwaitInput is reached (which pauses the
  conversation) or the script terminates ( null ).
  3. Flushes output buffers and persists session updates: session.flush(next == null) :
     * PersistenceManager Write: Iterates over the queue of MT (Mobile Terminated) messages generated during evaluation and calls persistenceManager.
     insertMT(mtMessage, this) for each message sent.
     * PersistenceManager Write / Delete:
     * If the conversation has ended ( next == null ): Calls persistenceManager.clearSession(this) to purge the session record from the database.
     * If the conversation is ongoing: Calls persistenceManager.saveSession(this) to serialize and upsert/save the active session state back to the
     database.



──────
### Postgres Database Read / Write Summary ( PostgresPersistenceManager )

  Method       | DB Action Type | Query constant           | Target Tables / Purpose
 ---------------------|----------------|-------------------------------------|---------------------------------------------------------------------------------
  getUser      | Read      | USER_PROFILE_BY_PLATFORM_ID_ROUTE | Retrieves user profile metadata ( brbl_users.user_platform_amalgams ,
|        |                   | platform_profiles ).
  getActiveRoutes  | Read      | SELECT_ALL_ROUTES_WITH_STATUS   | Retrieves route definitions ( brbl_logic.routes ).
  getKeywords    | Read      | SELECT_ALL_KEYWORDS        | Retrieves regex patterns for keywords ( brbl_logic.keywords ).
  getNodeGraph    | Read      | SELECT_SCRIPT_GRAPH_RECURSIVE   | Loads the recursive node tree for a script ( brbl_logic.nodes , edges ).
  loadSession    | Read      | SESSION_SELECT           | Reads active session byte arrays ( brbl_users.sessions ).
  insertNewUser   | Write     | Multiple Statements         | Persists new user rows ( brbl_users.user_platform_amalgams , amalgams ).
  updateUserStatus  | Write     | UPDATE_USER_STATUS         | Updates Opt-In/Out status ( brbl_users.user_platform_amalgams ).
  insertMT      | Write     | MT_MESSAGE_INSERT         | Logs outbound messages ( brbl_logs.mt_messages ).
  insertProcessedMO | Write     | MO_MESSAGE_PRCD          | Updates processed state of inbound messages ( brbl_logs.mo_messages ).
  saveSession    | Write     | SESSION_UPSERT           | Upserts active session byte streams ( brbl_users.sessions ).
  clearSession    | Write (Delete) | SESSION_DELETE           | Removes completed sessions ( brbl_users.sessions ).