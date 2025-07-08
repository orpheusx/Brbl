CREATE TABLE brbl_logic.nodes (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    text        VARCHAR(255),       --> SMS is limited to 160 chars but other platform have higher limits.
    type        SMALLINT NOT NULL,  --> see ScriptType enum for meaning.
    label       VARCHAR(32)         --> the name given to the node element in a UI
);

CREATE TABLE brbl_logic.edges (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    match_text      VARCHAR(128),       --> the text that must be matched to direct the conversation to the dst node
    response_text   VARCHAR(255),       --> the text emitted when the edge is selected.
    src             UUID,               --> FK to scripts table
    dst             UUID                --> FK to scripts table
    CONSTRAINT fk_script_src
        FOREIGN KEY(id) REFERENCES brbl_logic.nodes(id),
    CONSTRAINT fk_script_dst
        FOREIGN KEY(id) REFERENCES brbl_logic.nodes(id)
);

