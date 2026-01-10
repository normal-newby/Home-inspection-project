import sqlite3
import uuid

connector = sqlite3.connect("database.db")
cursor = connector.cursor()

num = int(input("How many inspection fields are we making? "))
places = [
    "roofing",
    "exterior",
    "structure",
    "electrical",
    "heating",
    "cooling",
    "insulation",
    "plumbing",
    "interior"
]
types = ["description", "limitations", "recommendations"]

inspectionPlace = inspectionType = fieldID= None

def choosePlace():
    global inspectionPlace
    print("Choose the place (1-9) ")
    for i, val in enumerate(places):
        print(f"{i+1}: {val}")
    inspectionPlace = places[int(input()) - 1]

def chooseType():
    global inspectionType
    print("Choose the type (1-3) ")
    for i, val in enumerate(types):
        print(f"{i+1}: {val}")
    inspectionType = types[int(input()) - 1]

def makeValues():
    numValues = int(input("How many values? "))
    for i in range(numValues):
        val = input(f"Value {i+1}: ")
        cursor.execute("""
            INSERT INTO inspection_field_definition_value (id, inspection_field_definition_id, value)
            VALUES (?, ?, ?)
        """, (str(uuid.uuid4()), fieldID, val)
        )
        


for i in range(num):
    choosePlace()
    chooseType()
    name = input("What is the name of this field? ")

    fieldID = str(uuid.uuid4())
    
    cursor.execute(
        """
        INSERT INTO inspection_field_definition (id, field_name, field_place, field_type)
        VALUES (?, ?, ?, ?)
        """, (fieldID, name, inspectionPlace, inspectionType)
    )

    makeValues()

connector.commit()
connector.close()

print("Done")