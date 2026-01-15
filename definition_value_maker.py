import sqlite3
import uuid

connector = sqlite3.connect("database.db")
cursor = connector.cursor()

if __name__ == "__main__":
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

fieldID= None

def choosePlace():
    print("Choose the place (1-9) ")
    for i, val in enumerate(places):
        print(f"{i+1}: {val}")
    inspectionPlace = places[int(input()) - 1]
    return inspectionPlace

def chooseType():
    print("Choose the type (1-3) ")
    for i, val in enumerate(types):
        print(f"{i+1}: {val}")
    inspectionType = types[int(input()) - 1]
    return inspectionType

def makeValues():
    numValues = int(input("How many values? "))
    for i in range(numValues):
        val = input(f"Value {i+1}: ")
        cursor.execute("""
            INSERT INTO inspection_field_definition_value (id, inspection_field_definition_id, value)
            VALUES (?, ?, ?)
        """, (str(uuid.uuid4()), fieldID, val)
        )
        

if __name__ == "__main__":
    for i in range(num):
        inspectionPlace = choosePlace()
        inspectionType = chooseType()
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