migrate((app) => {
    const users = app.findCollectionByNameOrId("users");

    const collections = [
        "businesses",
        "branches",
        "resources",
        "resource_types",
        "services",
        "service_category",
        "resource_assignments",
        "branch_services",
        "service_assignments",
        "discounts",
        "roles",
        "permissions"
    ];

    for (const collectionName of collections) {
        const collection = app.findCollectionByNameOrId(collectionName);

        // Add deleted_at if it doesn't already exist
        if (!collection.fields.getByName("deleted_at")) {
            collection.fields.add(
                new DateField({
                    name: "deleted_at",
                    required: false,
                })
            );
        }

        // Add deleted_by if it doesn't already exist
        if (!collection.fields.getByName("deleted_by")) {
            collection.fields.add(
                new RelationField({
                    name: "deleted_by",
                    collectionId: users.id,
                    required: false,
                    maxSelect: 1,
                    cascadeDelete: false,
                })
            );
        }

        app.save(collection);
    }
}, (app) => {
    const collections = [
        "businesses",
        "branches",
        "resources",
        "resource_types",
        "services",
        "service_category",
        "resource_assignments",
        "branch_services",
        "service_assignments",
        "discounts",
        "roles",
        "permissions"
    ];

    for (const collectionName of collections) {
        const collection = app.findCollectionByNameOrId(collectionName);

        collection.fields.removeByName("deleted_at");
        collection.fields.removeByName("deleted_by");

        app.save(collection);
    }
});