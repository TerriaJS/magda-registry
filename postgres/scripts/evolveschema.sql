CREATE TABLE IF NOT EXISTS Record (
    recordID varchar(100) PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS Section (
    sectionID varchar(100) PRIMARY KEY,
    name varchar(100)
);

CREATE TABLE IF NOT EXISTS RecordSection (
    recordID varchar(100) REFERENCES Record,
    sectionID varchar(100) REFERENCES Section,
    data jsonb
);

CREATE TABLE IF NOT EXISTS RecordHierarchy (
    parentID varchar(100) REFERENCES Record,
    childID varchar(100) REFERENCES Record
);
