package com.enoughisasgoodasafeast.operator;

public enum RouteStatus {
    REQUESTED,  // a Customer has requested a new channel for the given Platform. Awaiting approval from Platform owner.
    APPROVED,   // the Platform owner has approved the request, but it hasn't become active in our system.
    ACTIVE,     // the route is now active in our system.
    SUSPENDED,  // either the Platform owner or Brbl has deactivated, temporarily or permanently.
    LAPSED      // the Customer has discontinued use of the Route.
}
