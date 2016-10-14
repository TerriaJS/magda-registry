CREATE TABLE IF NOT EXISTS Record (
    recordID varchar(100) PRIMARY KEY,
    name varchar(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS Section (
    sectionID varchar(100) PRIMARY KEY,
    name varchar(100) NOT NULL,
    jsonSchema jsonb
);

CREATE TABLE IF NOT EXISTS RecordSection (
    recordID varchar(100) REFERENCES Record NOT NULL,
    sectionID varchar(100) REFERENCES Section NOT NULL,
    data jsonb NOT NULL
);

CREATE TABLE IF NOT EXISTS RecordHierarchy (
    parentID varchar(100) REFERENCES Record NOT NULL,
    childID varchar(100) REFERENCES Record NOT NULL
);

INSERT INTO Section (sectionID, name) VALUES ('1', 'First');
INSERT INTO Section (sectionID, name) VALUES ('2', 'Second');
INSERT INTO Record (recordID, name) VALUES ('A', 'First Record');
INSERT INTO Record (recordID, name) VALUES ('B', 'Second Record');
INSERT INTO RecordSection (recordID, sectionID, data) VALUES ('A', '1', '{}'::json);
INSERT INTO RecordSection (recordID, sectionID, data) VALUES ('A', '2', '{}'::json);
