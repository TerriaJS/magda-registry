CREATE TABLE IF NOT EXISTS Record (
    recordID varchar(100) PRIMARY KEY
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
