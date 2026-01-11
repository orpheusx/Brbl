--
-- Name: routes; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--
CREATE TABLE brbl_logic.routes (
                                   id uuid NOT NULL,
                                   platform public.platform NOT NULL,
                                   channel character varying(15) NOT NULL,
                                   default_node_id uuid NOT NULL,
                                   customer_id uuid NOT NULL,
                                   status brbl_logic.route_status DEFAULT 'REQUESTED'::brbl_logic.route_status NOT NULL,
                                   created_at timestamp with time zone NOT NULL,
                                   updated_at timestamp with time zone NOT NULL
);

