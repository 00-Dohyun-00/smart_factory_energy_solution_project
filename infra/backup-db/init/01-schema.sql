-- WATTever 현재 DB 구조 (Supabase에 적용된 01~11 SQL을 모두 반영한 최종본)
-- 백엔드 Entity 작성 기준. 이 파일을 Supabase에 다시 실행하지 마세요.


-- Name: site_power_minute; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.site_power_minute (
    ts timestamp without time zone NOT NULL,
    grid_kw numeric(10,2) NOT NULL,
    solar_kw numeric(10,2) NOT NULL,
    ess_kw numeric(10,2) NOT NULL,
    load_kw numeric(10,2) NOT NULL
);

-- Name: demand_interval_15m; Type: VIEW; Schema: public; Owner: -

CREATE VIEW public.demand_interval_15m AS
 SELECT date_bin('00:15:00'::interval, ts, '2026-01-01 00:00:00'::timestamp without time zone) AS interval_start,
    round(avg(grid_kw), 2) AS actual_avg_kw,
    round(avg((grid_kw + GREATEST(ess_kw, (0)::numeric))), 2) AS no_ess_avg_kw,
    round(avg(GREATEST(ess_kw, (0)::numeric)), 2) AS reduced_kw,
    count(*) AS minute_count
   FROM public.site_power_minute
  GROUP BY (date_bin('00:15:00'::interval, ts, '2026-01-01 00:00:00'::timestamp without time zone));

-- Name: equipment; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.equipment (
    equipment_id character varying(30) NOT NULL,
    zone_id bigint,
    name character varying(50) NOT NULL,
    equipment_role character varying(10) NOT NULL,
    rated_kw numeric(10,2),
    is_virtual boolean DEFAULT true NOT NULL,
    modbus_slave_id integer,
    mqtt_topic character varying(100),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT (now() AT TIME ZONE 'Asia/Seoul'::text) NOT NULL,
    CONSTRAINT equipment_equipment_role_check CHECK (((equipment_role)::text = ANY ((ARRAY['GRID'::character varying, 'SOLAR'::character varying, 'ESS'::character varying, 'LOAD'::character varying])::text[])))
);

-- Name: ess_status_minute; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.ess_status_minute (
    ts timestamp without time zone NOT NULL,
    power_kw numeric(10,2) NOT NULL,
    charge_kwh numeric(10,4) DEFAULT 0 NOT NULL,
    discharge_kwh numeric(10,4) DEFAULT 0 NOT NULL,
    soc numeric(5,2) NOT NULL,
    battery_temp numeric(5,2),
    status character varying(10) NOT NULL,
    CONSTRAINT ess_status_minute_status_check CHECK (((status)::text = ANY ((ARRAY['CHARGE'::character varying, 'DISCHARGE'::character varying, 'IDLE'::character varying])::text[])))
);

-- Name: event_log; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.event_log (
    event_id bigint NOT NULL,
    occurred_at timestamp without time zone NOT NULL,
    event_type character varying(15) NOT NULL,
    stage character varying(10),
    message text NOT NULL,
    recommendation_id bigint,
    CONSTRAINT event_log_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['STAGE_CHANGE'::character varying, 'RECOMMEND'::character varying, 'HANDLE'::character varying, 'INTERVAL_END'::character varying, 'COMM'::character varying, 'SETTING'::character varying])::text[]))),
    CONSTRAINT event_log_stage_check CHECK (((stage)::text = ANY ((ARRAY['NORMAL'::character varying, 'CAUTION'::character varying, 'WARNING'::character varying])::text[])))
);

-- Name: event_log_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.event_log_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: event_log_event_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -

ALTER SEQUENCE public.event_log_event_id_seq OWNED BY public.event_log.event_id;

-- Name: power_minute; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.power_minute (
    equipment_id character varying(30) NOT NULL,
    ts timestamp without time zone NOT NULL,
    avg_kw numeric(10,2) NOT NULL,
    max_kw numeric(10,2),
    min_kw numeric(10,2),
    energy_kwh numeric(10,4),
    grid_kwh numeric(10,4),
    solar_kwh numeric(10,4),
    ess_kwh numeric(10,4),
    is_running smallint DEFAULT 0 NOT NULL,
    CONSTRAINT power_minute_is_running_check CHECK ((is_running = ANY (ARRAY[0, 1])))
);

-- Name: raw_factory_load; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.raw_factory_load (
    recorded_at timestamp without time zone NOT NULL,
    source_recorded_at timestamp without time zone NOT NULL,
    factory_kw numeric(10,2) NOT NULL
);

-- Name: raw_equipment_load; Type: VIEW; Schema: public; Owner: -

CREATE VIEW public.raw_equipment_load AS
 WITH weighted AS (
         SELECT r.recorded_at,
            r.factory_kw,
            e.equipment_id,
            (e.rated_kw * (0.9 + ((0.2 * ((abs(hashtext(((e.equipment_id)::text || (r.recorded_at)::text))) % 1000))::numeric) / 1000.0))) AS weight
           FROM (public.raw_factory_load r
             CROSS JOIN public.equipment e)
          WHERE ((e.equipment_role)::text = 'LOAD'::text)
        ), calc AS (
         SELECT weighted.recorded_at,
            weighted.equipment_id,
            round(((weighted.factory_kw * weighted.weight) / sum(weighted.weight) OVER (PARTITION BY weighted.recorded_at)), 2) AS kw
           FROM weighted
        )
 SELECT recorded_at,
    equipment_id,
    kw,
        CASE
            WHEN (kw > (0)::numeric) THEN 1
            ELSE 0
        END AS is_running
   FROM calc;

-- Name: recommendation; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.recommendation (
    recommendation_id bigint NOT NULL,
    recommended_at timestamp without time zone NOT NULL,
    interval_start timestamp without time zone NOT NULL,
    action character varying(10) NOT NULL,
    power_kw numeric(10,2) DEFAULT 0 NOT NULL,
    reason_type character varying(25) NOT NULL,
    description text,
    predicted_avg_kw numeric(10,2),
    target_peak_kw numeric(10,2),
    status character varying(10) DEFAULT 'PENDING'::character varying NOT NULL,
    handled_by character varying(30),
    handled_at timestamp without time zone,
    CONSTRAINT recommendation_action_check CHECK (((action)::text = ANY ((ARRAY['CHARGE'::character varying, 'DISCHARGE'::character varying, 'IDLE'::character varying])::text[]))),
    CONSTRAINT recommendation_reason_type_check CHECK (((reason_type)::text = ANY ((ARRAY['PEAK_DEFENSE'::character varying, 'ON_PEAK_DISCHARGE'::character varying, 'SOLAR_SURPLUS_CHARGE'::character varying, 'OFF_PEAK_CHARGE'::character varying, 'SAFETY_LIMIT'::character varying, 'IDLE'::character varying])::text[]))),
    CONSTRAINT recommendation_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPLIED'::character varying, 'AUTO'::character varying, 'SKIPPED'::character varying, 'EXPIRED'::character varying])::text[])))
);

-- Name: recommendation_recommendation_id_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.recommendation_recommendation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: recommendation_recommendation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -

ALTER SEQUENCE public.recommendation_recommendation_id_seq OWNED BY public.recommendation.recommendation_id;

-- Name: setting; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.setting (
    setting_key character varying(40) NOT NULL,
    group_name character varying(15) NOT NULL,
    label character varying(50) NOT NULL,
    value character varying(50) NOT NULL,
    unit character varying(15),
    updated_at timestamp without time zone DEFAULT (now() AT TIME ZONE 'Asia/Seoul'::text) NOT NULL,
    updated_by character varying(30) DEFAULT '운영자'::character varying NOT NULL,
    CONSTRAINT setting_group_name_check CHECK (((group_name)::text = ANY ((ARRAY['PEAK_TARIFF'::character varying, 'ESS'::character varying, 'OPERATION'::character varying, 'SOLAR'::character varying, 'ENVIRONMENT'::character varying])::text[])))
);

-- Name: setting_history; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.setting_history (
    history_id bigint NOT NULL,
    changed_at timestamp without time zone DEFAULT (now() AT TIME ZONE 'Asia/Seoul'::text) NOT NULL,
    setting_key character varying(40) NOT NULL,
    old_value character varying(50),
    new_value character varying(50) NOT NULL,
    changed_by character varying(30) DEFAULT '운영자'::character varying NOT NULL
);

-- Name: setting_history_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.setting_history_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: setting_history_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -

ALTER SEQUENCE public.setting_history_history_id_seq OWNED BY public.setting_history.history_id;

-- Name: solar_power; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.solar_power (
    ts timestamp without time zone NOT NULL,
    power_type character varying(10) NOT NULL,
    power_kw numeric(10,2) NOT NULL,
    CONSTRAINT solar_power_power_type_check CHECK (((power_type)::text = ANY ((ARRAY['SOLAR'::character varying, 'GRID'::character varying, 'ESS'::character varying, 'LOAD'::character varying])::text[])))
);

-- Name: tariff_period; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.tariff_period (
    period_id bigint NOT NULL,
    season character varying(12) NOT NULL,
    period_type character varying(10) NOT NULL,
    start_time time without time zone NOT NULL,
    end_time time without time zone NOT NULL,
    unit_price numeric(6,1) NOT NULL,
    CONSTRAINT tariff_period_check CHECK ((start_time < end_time)),
    CONSTRAINT tariff_period_period_type_check CHECK (((period_type)::text = ANY ((ARRAY['OFF_PEAK'::character varying, 'MID_PEAK'::character varying, 'ON_PEAK'::character varying])::text[]))),
    CONSTRAINT tariff_period_season_check CHECK (((season)::text = ANY ((ARRAY['SUMMER'::character varying, 'SPRING_FALL'::character varying, 'WINTER'::character varying])::text[])))
);

-- Name: tariff_period_period_id_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.tariff_period_period_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: tariff_period_period_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -

ALTER SEQUENCE public.tariff_period_period_id_seq OWNED BY public.tariff_period.period_id;

-- Name: weather; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.weather (
    ts timestamp without time zone NOT NULL,
    temperature numeric(5,2),
    humidity numeric(5,2),
    cloud_cover numeric(5,2),
    sky character varying(10),
    radiation_wm2 numeric(7,2),
    source character varying(12) NOT NULL,
    precipitation_mm numeric(5,2),
    CONSTRAINT weather_sky_check CHECK (((sky)::text = ANY ((ARRAY['CLEAR'::character varying, 'CLOUDY'::character varying, 'OVERCAST'::character varying])::text[]))),
    CONSTRAINT weather_source_check CHECK (((source)::text = ANY ((ARRAY['ASOS'::character varying, 'OPEN_METEO'::character varying])::text[])))
);

-- Name: zone; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.zone (
    zone_id bigint NOT NULL,
    name character varying(30) NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    zone_code character varying(10) NOT NULL
);

-- Name: zone_zone_id_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.zone_zone_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Name: zone_zone_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -

ALTER SEQUENCE public.zone_zone_id_seq OWNED BY public.zone.zone_id;

-- Name: event_log event_id; Type: DEFAULT; Schema: public; Owner: -

ALTER TABLE ONLY public.event_log ALTER COLUMN event_id SET DEFAULT nextval('public.event_log_event_id_seq'::regclass);

-- Name: recommendation recommendation_id; Type: DEFAULT; Schema: public; Owner: -

ALTER TABLE ONLY public.recommendation ALTER COLUMN recommendation_id SET DEFAULT nextval('public.recommendation_recommendation_id_seq'::regclass);

-- Name: setting_history history_id; Type: DEFAULT; Schema: public; Owner: -

ALTER TABLE ONLY public.setting_history ALTER COLUMN history_id SET DEFAULT nextval('public.setting_history_history_id_seq'::regclass);

-- Name: tariff_period period_id; Type: DEFAULT; Schema: public; Owner: -

ALTER TABLE ONLY public.tariff_period ALTER COLUMN period_id SET DEFAULT nextval('public.tariff_period_period_id_seq'::regclass);

-- Name: zone zone_id; Type: DEFAULT; Schema: public; Owner: -

ALTER TABLE ONLY public.zone ALTER COLUMN zone_id SET DEFAULT nextval('public.zone_zone_id_seq'::regclass);

-- Name: equipment equipment_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.equipment
    ADD CONSTRAINT equipment_pkey PRIMARY KEY (equipment_id);

-- Name: ess_status_minute ess_status_minute_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.ess_status_minute
    ADD CONSTRAINT ess_status_minute_pkey PRIMARY KEY (ts);

-- Name: event_log event_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.event_log
    ADD CONSTRAINT event_log_pkey PRIMARY KEY (event_id);

-- Name: power_minute power_minute_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.power_minute
    ADD CONSTRAINT power_minute_pkey PRIMARY KEY (equipment_id, ts);

-- Name: raw_factory_load raw_factory_load_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.raw_factory_load
    ADD CONSTRAINT raw_factory_load_pkey PRIMARY KEY (recorded_at);

-- Name: recommendation recommendation_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.recommendation
    ADD CONSTRAINT recommendation_pkey PRIMARY KEY (recommendation_id);

-- Name: setting_history setting_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.setting_history
    ADD CONSTRAINT setting_history_pkey PRIMARY KEY (history_id);

-- Name: setting setting_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.setting
    ADD CONSTRAINT setting_pkey PRIMARY KEY (setting_key);

-- Name: site_power_minute site_power_minute_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.site_power_minute
    ADD CONSTRAINT site_power_minute_pkey PRIMARY KEY (ts);

-- Name: solar_power solar_power_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.solar_power
    ADD CONSTRAINT solar_power_pkey PRIMARY KEY (ts, power_type);

-- Name: tariff_period tariff_period_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tariff_period
    ADD CONSTRAINT tariff_period_pkey PRIMARY KEY (period_id);

-- Name: weather weather_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.weather
    ADD CONSTRAINT weather_pkey PRIMARY KEY (ts);

-- Name: zone zone_name_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.zone
    ADD CONSTRAINT zone_name_key UNIQUE (name);

-- Name: zone zone_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.zone
    ADD CONSTRAINT zone_pkey PRIMARY KEY (zone_id);

-- Name: zone zone_zone_code_key; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.zone
    ADD CONSTRAINT zone_zone_code_key UNIQUE (zone_code);

-- Name: idx_event_log_time; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_event_log_time ON public.event_log USING btree (occurred_at);

-- Name: idx_power_minute_ts; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_power_minute_ts ON public.power_minute USING btree (ts);

-- Name: idx_recommendation_interval; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_recommendation_interval ON public.recommendation USING btree (interval_start);

-- Name: idx_recommendation_time; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_recommendation_time ON public.recommendation USING btree (recommended_at);

-- Name: idx_setting_history_key_time; Type: INDEX; Schema: public; Owner: -

CREATE INDEX idx_setting_history_key_time ON public.setting_history USING btree (setting_key, changed_at);

-- Name: equipment equipment_zone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.equipment
    ADD CONSTRAINT equipment_zone_id_fkey FOREIGN KEY (zone_id) REFERENCES public.zone(zone_id);

-- Name: event_log event_log_recommendation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.event_log
    ADD CONSTRAINT event_log_recommendation_id_fkey FOREIGN KEY (recommendation_id) REFERENCES public.recommendation(recommendation_id);

-- Name: power_minute power_minute_equipment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.power_minute
    ADD CONSTRAINT power_minute_equipment_id_fkey FOREIGN KEY (equipment_id) REFERENCES public.equipment(equipment_id);

-- Name: setting_history setting_history_setting_key_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.setting_history
    ADD CONSTRAINT setting_history_setting_key_fkey FOREIGN KEY (setting_key) REFERENCES public.setting(setting_key);

-- Name: equipment; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.equipment ENABLE ROW LEVEL SECURITY;

-- Name: ess_status_minute; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.ess_status_minute ENABLE ROW LEVEL SECURITY;

-- Name: event_log; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.event_log ENABLE ROW LEVEL SECURITY;

-- Name: power_minute; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.power_minute ENABLE ROW LEVEL SECURITY;

-- Name: raw_factory_load; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.raw_factory_load ENABLE ROW LEVEL SECURITY;

-- Name: recommendation; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.recommendation ENABLE ROW LEVEL SECURITY;

-- Name: setting; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.setting ENABLE ROW LEVEL SECURITY;

-- Name: setting_history; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.setting_history ENABLE ROW LEVEL SECURITY;

-- Name: site_power_minute; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.site_power_minute ENABLE ROW LEVEL SECURITY;

-- Name: solar_power; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.solar_power ENABLE ROW LEVEL SECURITY;

-- Name: tariff_period; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.tariff_period ENABLE ROW LEVEL SECURITY;

-- Name: weather; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.weather ENABLE ROW LEVEL SECURITY;

-- Name: zone; Type: ROW SECURITY; Schema: public; Owner: -

ALTER TABLE public.zone ENABLE ROW LEVEL SECURITY;

