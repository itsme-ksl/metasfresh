-- 2019-02-15T18:48:50.406
-- I forgot to set the DICTIONARY_ID_COMMENTS System Configurator
/* DDL */ CREATE TABLE public.C_Phonecall_Schedule (AD_Client_ID NUMERIC(10) NOT NULL, AD_Org_ID NUMERIC(10) NOT NULL, AD_User_ID NUMERIC(10) NOT NULL, BufferHours NUMERIC(10), C_BPartner_ID NUMERIC(10) NOT NULL, C_BPartner_Location_ID NUMERIC(10) NOT NULL, C_Phonecall_Schedule_ID NUMERIC(10) NOT NULL, C_Phonecall_Schema_ID NUMERIC(10) NOT NULL, C_Phonecall_Schema_Version_ID NUMERIC(10) NOT NULL, C_Phonecall_Schema_Version_Line_ID NUMERIC(10) NOT NULL, Created TIMESTAMP WITH TIME ZONE NOT NULL, CreatedBy NUMERIC(10) NOT NULL, IsActive CHAR(1) CHECK (IsActive IN ('Y','N')) NOT NULL, IsManualPhonecall CHAR(1) DEFAULT 'N' CHECK (IsManualPhonecall IN ('Y','N')) NOT NULL, PhonecallDate TIMESTAMP WITHOUT TIME ZONE NOT NULL, PhonecallTimeMax TIMESTAMP WITHOUT TIME ZONE NOT NULL, PhonecallTimeMin TIMESTAMP WITHOUT TIME ZONE NOT NULL, Processed CHAR(1) DEFAULT 'N' CHECK (Processed IN ('Y','N')) NOT NULL, Updated TIMESTAMP WITH TIME ZONE NOT NULL, UpdatedBy NUMERIC(10) NOT NULL, CONSTRAINT ADUser_CPhonecallSchedule FOREIGN KEY (AD_User_ID) REFERENCES public.AD_User DEFERRABLE INITIALLY DEFERRED, CONSTRAINT CBPartner_CPhonecallSchedule FOREIGN KEY (C_BPartner_ID) REFERENCES public.C_BPartner DEFERRABLE INITIALLY DEFERRED, CONSTRAINT CBPartnerLocation_CPhonecallSchedule FOREIGN KEY (C_BPartner_Location_ID) REFERENCES public.C_BPartner_Location DEFERRABLE INITIALLY DEFERRED, CONSTRAINT C_Phonecall_Schedule_Key PRIMARY KEY (C_Phonecall_Schedule_ID), CONSTRAINT CPhonecallSchema_CPhonecallSchedule FOREIGN KEY (C_Phonecall_Schema_ID) REFERENCES public.C_Phonecall_Schema DEFERRABLE INITIALLY DEFERRED, CONSTRAINT CPhonecallSchemaVersion_CPhonecallSchedule FOREIGN KEY (C_Phonecall_Schema_Version_ID) REFERENCES public.C_Phonecall_Schema_Version DEFERRABLE INITIALLY DEFERRED, CONSTRAINT CPhonecallSchemaVersionLine_CPhonecallSchedule FOREIGN KEY (C_Phonecall_Schema_Version_Line_ID) REFERENCES public.C_Phonecall_Schema_Version_Line DEFERRABLE INITIALLY DEFERRED)
;

